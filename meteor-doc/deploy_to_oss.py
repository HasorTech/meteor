#!/usr/bin/env python3
"""Build, validate and upload a documentation artifact to its configured OSS prefix."""

import mimetypes
import os
import re
import subprocess
import sys
from urllib.parse import urlsplit

from deploy_config import DOCUMENT_DIR
from deploy_site import SITE_NAME


def build_project(output, config, mode):
    env = os.environ.copy()
    if mode != "portal":
        cdn = urlsplit(config["cdnUrl"])
        env["DOCS_URL"] = config.get("publicUrl", f"{cdn.scheme}://{cdn.netloc}")
        env["DOCS_BASE_URL"] = config.get("baseUrl", "/")
    command = f"build:{mode}"
    subprocess.run(["npm", "run", command, "--", "--out-dir", str(output)],
                   cwd=DOCUMENT_DIR, env=env, check=True)


def artifact_paths(mode):
    locales = ("cn", "en")
    if SITE_NAME == "dataql":
        entries = ["dataql/index.html", "dataway/index.html"]
        if mode == "alone":
            entries.append("index.html")
        for locale in locales:
            entries.extend(f"{locale}/{project}/index.html" for project in ("dataql", "dataway"))
            entries.extend((f"{locale}/dataql/projects/index.html", f"{locale}/dataql-sitemap.xml"))
        return entries
    project = f"{SITE_NAME}/" if mode == "portal" and SITE_NAME != "www" else ""
    entries = [f"{project}index.html"]
    for locale in locales:
        entries.extend((f"{locale}/{project}index.html", f"{locale}/{project}sitemap.xml"))
    if SITE_NAME == "www":
        entries.extend(("robots.txt", "hasor-tech-search/pagefind.js"))
    return entries


def owns_portal_path(name):
    parts = name.split("/")
    if SITE_NAME == "www":
        reserved = {"hasor", "dbvisitor", "dataql", "dataway", "meteor", "neta", "cobble"}
        reserved.update({"dataql-sitemap.xml", "files", "llms.txt", "search-index.json"})
        return parts[0] in {"index.html", "robots.txt", "hasor-tech-search", "schema", "rsf"} or (
            parts[0] in {"cn", "en"} and len(parts) > 1 and parts[1] not in reserved)
    if SITE_NAME == "dataql":
        if name in {"dataql/index.html", "dataway/index.html"}:
            return True
        if parts[0] not in {"cn", "en"} or len(parts) < 2:
            return False
        if parts[1] in {"dataql", "dataway", "dataql-sitemap.xml", "files", "llms.txt", "search-index.json"}:
            return True
        if parts[1] == "assets":
            # The portal and DataQL can publish the same immutable, content-hashed asset.
            return bool(re.search(r"[.-][0-9a-f]{8,}\.[^.]+(?:\.LICENSE\.txt)?$", parts[-1]))
        return len(parts) > 2 and parts[1] == "img" and parts[2] in {
            "dataql", "dataway", "project-home", "labels", "favicon"}
    return name == f"{SITE_NAME}/index.html" or name.startswith((f"cn/{SITE_NAME}/", f"en/{SITE_NAME}/"))


def collect_files(output, mode):
    files = sorted(output.rglob("*"))
    if output.is_symlink() or any(file.is_symlink() for file in files):
        raise ValueError("The documentation build must not contain symbolic links.")
    for name in artifact_paths(mode):
        if not (output / name).is_file():
            raise FileNotFoundError(f"Missing documentation artifact: {name}")
    files = [file for file in files if file.is_file()]
    if mode == "portal":
        files = [file for file in files if owns_portal_path(file.relative_to(output).as_posix())]
    return files


def upload_to_oss(config, output, files):
    import oss2

    bucket = oss2.Bucket(oss2.Auth(config["accessKeyId"], config["accessKeySecret"]),
                         config["endpoint"], config["bucketName"])
    for file in files:
        object_name = config["prefix"] + file.relative_to(output).as_posix()
        content_type = mimetypes.guess_type(file.name)[0]
        headers = {"Content-Type": content_type} if content_type else {}
        try:
            bucket.put_object_from_file(object_name, str(file), headers=headers)
        except Exception as error:
            raise RuntimeError(f"OSS upload failed for {object_name}; CDN refresh must not proceed.") from error
        print(f"Uploaded: {object_name}")
    print(f"Upload complete: {len(files)} files.")


if __name__ == "__main__":
    from deploy import main

    sys.argv.append("--upload-only")
    sys.exit(main())
