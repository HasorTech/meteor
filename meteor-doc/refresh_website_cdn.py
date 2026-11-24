#!/usr/bin/env python3
"""Submit a directory refresh for the selected documentation site's CDN."""

import argparse
import json
import sys

from deploy_config import deployment_lock, load_config
from deploy_site import SITE_NAME


def refresh_targets(config, mode):
    root = config["cdnUrl"].rstrip("/") + "/"
    if mode == "alone" or SITE_NAME == "www":
        return {"Directory": [root]}
    projects = ("dataql", "dataway") if SITE_NAME == "dataql" else (SITE_NAME,)
    directories = [root + f"{locale}/{project}/" for project in projects for locale in ("cn", "en")]
    files = [root + project + "/" for project in projects]
    if SITE_NAME == "dataql":
        directories.extend(root + locale + "/" + directory + "/" for locale in ("cn", "en")
                           for directory in ("files", "img/dataql", "img/dataway", "img/project-home",
                                             "img/labels", "img/favicon"))
        files.extend(root + locale + "/" + file for locale in ("cn", "en")
                     for file in ("dataql-sitemap.xml", "llms.txt", "search-index.json"))
    return {"Directory": directories, "File": files}


def refresh_cdn(config, mode):
    from aliyunsdkcore.client import AcsClient
    from aliyunsdkcdn.request.v20180510 import RefreshObjectCachesRequest

    client = AcsClient(config["accessKeyId"], config["accessKeySecret"],
                       config.get("cdnRegionId", "cn-hangzhou"))
    for kind, urls in refresh_targets(config, mode).items():
        request = RefreshObjectCachesRequest.RefreshObjectCachesRequest()
        request.set_ObjectPath("\n".join(urls))
        request.set_ObjectType(kind)
        response = json.loads(client.do_action_with_exception(request))
        print(f"CDN refresh submitted. RefreshTaskId: {response.get('RefreshTaskId')}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("alone", "portal"),
                        default="portal" if SITE_NAME == "www" else "alone")
    args = parser.parse_args()
    try:
        with deployment_lock(args.mode):
            refresh_cdn(load_config(args.mode), args.mode)
    except Exception as error:
        print(f"CDN refresh failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
