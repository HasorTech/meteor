package net.hasor.meteor.address;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AddressUnitGroupsTest {
    @TempDir
    Path directory;

    @Test
    void equalAddressesRetainMembershipInBothUnits() throws Exception {
        InterAddress shared = new InterAddress("jsonrpc://127.0.0.1:28001/api/Orders");
        AddressPool pool = new AddressPool("east", 30000);
        pool.appendAddress("service", List.of());
        pool.refreshAddress("service", Map.of("east", List.of(shared), "west", List.of(shared)));
        assertEquals(2, pool.queryAddressesByUnit("service").size());
        assertEquals(Set.of(shared), pool.getBucket("service").getUnitAddresses("east"));
        assertEquals(Set.of(shared), pool.getBucket("service").getUnitAddresses("west"));
        assertEquals(List.of(shared), pool.queryAllAddresses("service"));
        pool.refreshAddress("service", Map.of("west", List.of(shared)));
        assertTrue(pool.getBucket("service").getUnitAddresses("east").isEmpty());
        assertEquals(Set.of(shared), pool.getBucket("service").getUnitAddresses("west"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "units.xml", "units-dtd.xml" })
    void xmlUnitMembershipIsSeparateFromTheFullPath(String resource) throws Exception {
        AddressPool pool = new AddressPool("east", 30000);
        String service = "[billing]order-query-1.0";
        pool.appendAddress(service, List.of());
        pool.loadAddressBook(getClass().getClassLoader(), "classpath:address-book/" + resource);
        AddressBucket bucket = pool.getBucket(service);
        assertEquals(2, bucket.getAddressesByUnit(AddressTypeEnum.Static).size());
        assertEquals(1, bucket.getUnitAddresses("east").size());
        assertEquals(2, bucket.getUnitAddresses("west").size());
        assertEquals("/api/Orders", bucket.getUnitAddresses("east").iterator().next().getPath());
        pool.refreshAddress(service, Map.of());
        assertEquals(2, pool.queryAllAddresses(service).size());
    }

    @Test
    void diskSnapshotKeepsStaticAndDiscoveredUnitGroups() throws Exception {
        AddressBucket source = new AddressBucket("service", "east");
        InterAddress address = new InterAddress("jsonrpc://127.0.0.1:28001/api/Orders");
        source.newAddress(Map.of("east", List.of(address)), AddressTypeEnum.Static);
        source.newAddress(Map.of("west", List.of(address)), AddressTypeEnum.Dynamic);
        AddressBucket restored = new AddressBucket("service", "east");
        try (DiskCache cache = new DiskCache(new AddressPool(), this.directory.toFile(), 3600000, 3600000)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            assertTrue(cache.saveToZip(source, output));
            cache.readFromZip(restored, new ByteArrayInputStream(output.toByteArray()));
        }
        assertEquals(source.getAddressesByUnit(AddressTypeEnum.Static), restored.getAddressesByUnit(AddressTypeEnum.Static));
        assertEquals(source.getAddressesByUnit(AddressTypeEnum.Dynamic), restored.getAddressesByUnit(AddressTypeEnum.Dynamic));
    }

    @Test
    void addressesWithoutAUnitUseTheConsumerUnit() throws Exception {
        // 未指定单元的动态地址归属当前消费端，显式单元保持不变。
        AddressPool pool = new AddressPool("east", 30000);
        InterAddress local = new InterAddress("rsf://127.0.0.1:28001");
        InterAddress remote = new InterAddress("rsf://127.0.0.1:28002");
        pool.appendAddress("service", local);
        pool.appendAddress("service", Map.of("west", List.of(remote)));
        assertEquals(Map.of("east", List.of(local), "west", List.of(remote)), pool.queryAddressesByUnit("service"));

        // 全量刷新同样采用当前消费端单元。
        pool.refreshAddress("service", List.of(local));
        assertEquals(Map.of("east", List.of(local)), pool.queryAddressesByUnit("service"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "local-unit.xml", "local-unit-dtd.xml" })
    void xmlWithoutAUnitUsesTheConsumerUnit(String resource) throws Exception {
        // XML 未配置单元时，加载到哪个消费端就归属哪个本地单元。
        AddressPool pool = new AddressPool("east", 30000);
        String service = "[billing]order-query-1.0";
        pool.appendAddress(service, List.of());
        pool.loadAddressBook(getClass().getClassLoader(), "classpath:address-book/" + resource);
        InterAddress address = new InterAddress("rsf://127.0.0.1:28001");
        assertEquals(Map.of("east", List.of(address)), pool.getBucket(service).getAddressesByUnit(AddressTypeEnum.Static));
    }

    @Test
    void dynamicRefreshKeepsTheStaticMembershipOfTheSameAddress() throws Exception {
        // 同一地址的静态来源和动态来源可以有不同单元。
        AddressBucket bucket = new AddressBucket("service", "east");
        InterAddress address = new InterAddress("rsf://127.0.0.1:28001");
        bucket.newAddress(List.of(address), AddressTypeEnum.Static);
        bucket.newAddress(Map.of("west", List.of(address)), AddressTypeEnum.Dynamic);

        // 撤回动态来源，不撤回静态来源及其单元归属。
        bucket.refreshAddressToNew(Map.of());
        assertEquals(Map.of("east", List.of(address)), bucket.getAddressesByUnit());
        assertEquals(List.of(address), bucket.getStaticAddresses());
        assertEquals(List.of(address), bucket.getAllAddresses());
        bucket.invalidAddress(address, 30000);
        assertEquals(List.of(address), bucket.getAvailableAddresses());
    }

    @Test
    void appendedUnitMembershipIsMergedAndRemovedWithItsAddress() throws Exception {
        // 同来源、同地址多次追加时合并单元，对外地址列表仍然去重。
        AddressBucket bucket = new AddressBucket("service", "east");
        InterAddress address = new InterAddress("rsf://127.0.0.1:28001");
        bucket.newAddress(Map.of("east", List.of(address)), AddressTypeEnum.Static);
        bucket.newAddress(Map.of("west", List.of(address)), AddressTypeEnum.Static);
        assertEquals(Set.of(address), bucket.getUnitAddresses("east"));
        assertEquals(Set.of(address), bucket.getUnitAddresses("west"));
        assertEquals(List.of(address), bucket.getStaticAddresses());

        // 删除地址同时移除所有单元归属及静态列表引用。
        bucket.removeAddress(address);
        assertTrue(bucket.getAddressesByUnit().isEmpty());
        assertTrue(bucket.getStaticAddresses().isEmpty());
        assertTrue(bucket.getAllAddresses().isEmpty());
    }

    @Test
    void snapshotRestoresTheRecordedUnitInsteadOfTheRestoringConsumerUnit() throws Exception {
        // 未指定单元时先绑定源端单元，持久化后不再随恢复端单元变化。
        AddressBucket source = new AddressBucket("service", "east");
        InterAddress address = new InterAddress("rsf://127.0.0.1:28001");
        source.newAddress(List.of(address), AddressTypeEnum.Static);
        source.newAddress(Map.of("west", List.of(address)), AddressTypeEnum.Dynamic);
        AddressBucket restored = new AddressBucket("service", "north");
        try (DiskCache cache = new DiskCache(new AddressPool(), this.directory.toFile(), 3600000, 3600000)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            assertTrue(cache.saveToZip(source, output));
            cache.readFromZip(restored, new ByteArrayInputStream(output.toByteArray()));
        }
        assertEquals(Map.of("east", List.of(address)), restored.getAddressesByUnit(AddressTypeEnum.Static));
        assertEquals(Map.of("west", List.of(address)), restored.getAddressesByUnit(AddressTypeEnum.Dynamic));
        assertTrue(restored.getUnitAddresses("north").isEmpty());
    }

    @Test
    void pathsPreserveCaseAndEscapedSegments() throws Exception {
        InterAddress upper = new InterAddress("JSONRPC://LOCALHOST:28001/api/Order%2FItem");
        InterAddress lower = new InterAddress("jsonrpc://localhost:28001/api/order%2FItem");
        assertNotEquals(upper, lower);
        assertEquals("/api/Order%2FItem", upper.getPath());
        assertEquals(upper, new InterAddress(upper.toURI()));
        assertEquals(upper, new InterAddress(upper.getSchema(), upper.getHost(), upper.getPort(), upper.getPath()));
    }
}
