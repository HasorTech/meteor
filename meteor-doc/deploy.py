#!/usr/bin/env python3
"""Build and publish one documentation site using one configuration snapshot."""

import argparse
import sys
from pathlib import Path
from tempfile import TemporaryDirectory

from deploy_config import deployment_lock, load_config
from deploy_site import SITE_NAME
from deploy_to_oss import build_project, collect_files, upload_to_oss
from refresh_website_cdn import refresh_cdn, refresh_targets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Build and validate without uploading or refreshing.")
    parser.add_argument("--mode", choices=("alone", "portal"), help="Select the build mode for this publication.")
    parser.add_argument("--upload-only", action="store_true", help="Upload without submitting a CDN refresh.")
    parser.add_argument("--artifact", type=Path, help="Validate an existing build in dry-run mode.")
    args = parser.parse_args()
    if args.artifact and not args.dry_run:
        parser.error("--artifact requires --dry-run")
    mode = args.mode or ("portal" if SITE_NAME == "www" else "alone")
    try:
        with deployment_lock(mode):
            config = load_config(mode)
            action = "Dry run" if args.dry_run else "Publishing"
            print(f"{action} {config['siteName']} to oss://{config['bucketName']}/{config['prefix']}", flush=True)
            with TemporaryDirectory(prefix="hasor-docs-") as directory:
                output = args.artifact.resolve() if args.artifact else Path(directory) / "site"
                if not args.artifact:
                    build_project(output, config, mode)
                files = collect_files(output, mode)
                print(f"Validated {len(files)} files.", flush=True)
                if args.dry_run:
                    for name in (file.relative_to(output).as_posix() for file in files):
                        print(f"OSS key: {config['prefix']}{name}")
                    if not args.upload_only:
                        for kind, urls in refresh_targets(config, mode).items():
                            for url in urls:
                                print(f"CDN {kind}: {url}")
                    print("Dry run complete. Skipped OSS upload and CDN refresh.")
                else:
                    upload_to_oss(config, output, files)
                    if not args.upload_only:
                        refresh_cdn(config, mode)
    except Exception as error:
        print(f"Publication failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
