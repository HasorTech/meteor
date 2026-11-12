/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.component.debug;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.asm.*;
import net.hasor.meteor.registry.admin.description.*;

/** 在独立类加载器中恢复调用契约；控制台无需安装业务接口 JAR。 */
public final class DebugContract extends ClassLoader {
    private final Map<String, byte[]> definitions = new LinkedHashMap<>();
    private final MetServiceDescription description;

    public DebugContract(MetServiceDescription description) {
        super(DebugContract.class.getClassLoader());
        this.description = description;
        for (MetTypeDescription type : description.getTypes().values()) {
            if (!Set.of("scalar", "void", "any", "object", "enum", "array", "collection", "map").contains(type.getKind())) {
                throw new IllegalArgumentException("暂不支持该契约类型的在线调试：" + type.getKind());
            }
            if (StringUtils.equals(type.getKind(), "object") && !type.getJavaType().startsWith("java.")) {
                this.definitions.put(type.getJavaType(), bean(type));
            } else if (StringUtils.equals(type.getKind(), "enum")) {
                throw new IllegalArgumentException("暂不支持枚举契约的在线调试：" + type.getJavaType());
            }
        }
        this.definitions.put(description.getJavaType(), service());
    }

    public Class<?> serviceType() throws ClassNotFoundException {
        return loadClass(this.description.getJavaType());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (!this.definitions.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                byte[] bytes = this.definitions.get(name);
                type = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    private byte[] service() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                internal(this.description.getJavaType()), null, "java/lang/Object", null);
        AnnotationVisitor service = writer.visitAnnotation("Lnet/hasor/meteor/annotation/MetService;", true);
        if (this.description.getContractServiceId() != null) service.visit("id", this.description.getContractServiceId());
        service.visitEnd();
        for (MetMethodDescription method : this.description.getMethods()) {
            StringBuilder descriptor = new StringBuilder("(");
            StringBuilder signature = new StringBuilder("(");
            for (MetFieldDescription param : method.getParameters()) {
                descriptor.append(descriptor(param.getType()));
                signature.append(signature(param.getType()));
            }
            descriptor.append(')').append(descriptor(method.getReturns().getType()));
            signature.append(')').append(signature(method.getReturns().getType()));
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    method.getJavaName(), descriptor.toString(), signature.toString(), null);
            AnnotationVisitor annotation = mv.visitAnnotation("Lnet/hasor/meteor/annotation/MetMethod;", true);
            if (method.getMethodId() != null) annotation.visit("id", method.getMethodId());
            annotation.visit("name", method.getName());
            annotation.visitEnd();
            for (int i = 0; i < method.getParameters().size(); i++) {
                field(mv.visitParameterAnnotation(i, "Lnet/hasor/meteor/annotation/MetParam;", true), method.getParameters().get(i));
            }
            mv.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] bean(MetTypeDescription type) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner = internal(type.getJavaType());
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", new String[] { "java/io/Serializable" });
        AnnotationVisitor annotation = writer.visitAnnotation("Lnet/hasor/meteor/annotation/MetType;", true);
        annotation.visit("name", type.getName());
        annotation.visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        for (MetFieldDescription property : type.getProperties()) {
            String name = property.getJavaName();
            String desc = descriptor(property.getType());
            FieldVisitor fv = writer.visitField(Opcodes.ACC_PUBLIC, name, desc, signature(property.getType()), null);
            field(fv.visitAnnotation("Lnet/hasor/meteor/annotation/MetField;", true), property);
            fv.visitEnd();
            String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Type fieldType = Type.getType(desc);
            MethodVisitor getter = writer.visitMethod(Opcodes.ACC_PUBLIC, "get" + suffix, "()" + desc,
                    "()" + signature(property.getType()), null);
            getter.visitCode();
            getter.visitVarInsn(Opcodes.ALOAD, 0);
            getter.visitFieldInsn(Opcodes.GETFIELD, owner, name, desc);
            getter.visitInsn(fieldType.getOpcode(Opcodes.IRETURN));
            getter.visitMaxs(0, 0);
            getter.visitEnd();
            MethodVisitor setter = writer.visitMethod(Opcodes.ACC_PUBLIC, "set" + suffix, "(" + desc + ")V",
                    "(" + signature(property.getType()) + ")V", null);
            setter.visitCode();
            setter.visitVarInsn(Opcodes.ALOAD, 0);
            setter.visitVarInsn(fieldType.getOpcode(Opcodes.ILOAD), 1);
            setter.visitFieldInsn(Opcodes.PUTFIELD, owner, name, desc);
            setter.visitInsn(Opcodes.RETURN);
            setter.visitMaxs(0, 0);
            setter.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private String descriptor(String reference) {
        MetTypeDescription type = this.description.getTypes().get(reference);
        if (type == null) throw new IllegalArgumentException("缺少类型声明：" + reference);
        if (StringUtils.equals(type.getKind(), "array")) return "[" + descriptor(type.getElementType());
        return switch (type.getJavaType()) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "short" -> "S";
            case "char" -> "C";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + internal(type.getJavaType()) + ";";
        };
    }

    private String signature(String reference) {
        MetTypeDescription type = this.description.getTypes().get(reference);
        if (StringUtils.equals(type.getKind(), "array")) return "[" + signature(type.getElementType());
        String desc = descriptor(reference);
        if (type.getArguments().isEmpty()) return desc;
        StringBuilder signature = new StringBuilder(desc.substring(0, desc.length() - 1)).append('<');
        for (String argument : type.getArguments()) signature.append(signature(argument));
        return signature.append(">;").toString();
    }

    private static String internal(String name) {
        if (name == null || name.contains("/") || name.contains(";") || name.contains("<")) {
            throw new IllegalArgumentException("无效的契约类型：" + name);
        }
        return name.replace('.', '/');
    }

    private static void field(AnnotationVisitor annotation, MetFieldDescription field) {
        if (field.getFieldId() != null) annotation.visit("id", field.getFieldId().intValue());
        if (field.getName() != null) annotation.visit("name", field.getName());
        annotation.visitEnd();
    }
}
