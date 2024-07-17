import os
import json
import pandas as pd
import requests
from urllib.parse import urlparse
from functools import lru_cache

GITHUB_TOKEN = 'github_pat_'


LICENSE_NOT_FOUND = 'No license found'
REPO_NOT_FOUND = 'Repository not found'

MAX_RETRIES = 3
RETRY_DELAY = 5  # seconds

@lru_cache(maxsize=None)
def get_license_info(repo_name):
    api_url = f"https://api.github.com/repos/{repo_name}"
    headers = {'Authorization': f'token {GITHUB_TOKEN}'}
    
    for attempt in range(MAX_RETRIES):
        response = requests.get(api_url, headers=headers)
        if response.status_code == 200:
            json_response = response.json()
            if 'license' in json_response and json_response['license'] is not None:
                return json_response['license'].get('spdx_id', LICENSE_NOT_FOUND)
            elif 'license' in json_response and json_response['license'] is None:
                return LICENSE_NOT_FOUND
        elif response.status_code == 401:
            print(f"Attempt {attempt + 1}/{MAX_RETRIES}: Received 401 error for {repo_name}. Retrying in {RETRY_DELAY} seconds...")
            time.sleep(RETRY_DELAY)
            continue
        else:
            break
    print(f"Failed to get license info for {repo_name}. Status code: {response.status_code}")
    return LICENSE_NOT_FOUND

def get_repo_name_from_url(url):
    parsed_url = urlparse(url)
    path_parts = parsed_url.path.strip('/').split('/')
    if len(path_parts) >= 2:
        return '/'.join(path_parts[:2])
    return None

def update_json_file(file_path, mapping):
    with open(file_path, 'r', encoding="utf-8") as file:
        entry = json.load(file)

    updatedDependency = entry.get('updatedDependency', {})
    github_compare_link = updatedDependency.get('githubCompareLink', '')

    dependency_repo_name = None
    if github_compare_link and 'https://github.com' in github_compare_link:
        dependency_repo_name = get_repo_name_from_url(github_compare_link)
    else:
        group_id = updatedDependency.get('dependencyGroupID')
        artifact_id = updatedDependency.get('dependencyArtifactID')
        repo_info = next((item for item in mapping if item["updatedDependency.dependencyGroupID"] == group_id and (item["updatedDependency.dependencyArtifactID"] == artifact_id or item["updatedDependency.dependencyArtifactID"] == "*")), None)
        if repo_info:
            dependency_repo_name = get_repo_name_from_url(repo_info["githubRepoLink"])

    if dependency_repo_name:
        license_info = get_license_info(dependency_repo_name)
        updatedDependency['licenseInfo'] = license_info
        updatedDependency['githubRepoSlug'] = dependency_repo_name
    else:
        updatedDependency['licenseInfo'] = LICENSE_NOT_FOUND
        updatedDependency['githubRepoSlug'] = REPO_NOT_FOUND

    assert updatedDependency['licenseInfo'], "licenseInfo field is empty"
    assert updatedDependency['githubRepoSlug'], "githubRepoSlug field is empty"

    # parse urls like this into their github slug https://github.com/versly/wsdoc/pull/80
    main_pr_url = entry.get('url')
    if main_pr_url:
        entry['licenseInfo'] = get_license_info(get_repo_name_from_url(main_pr_url))
    elif not entry['licenseInfo']:
        entry['licenseInfo'] = LICENSE_NOT_FOUND
    # Assert that the new field in the main entry is filled
    assert entry['licenseInfo'], "licenseInfo field in the main entry is empty"

    with open(file_path, 'w', encoding="utf-8") as file:
        file_text = json.dumps(entry, indent=2)
        file.write(file_text.replace('": ', '" : '))

if __name__ == '__main__':
    
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'manual_repo_mapping.json'), 'r', encoding="utf-8") as file:
        mapping = json.load(file)
    
    BENCHMARK_DIRS = [os.path.join(os.path.dirname(os.path.abspath(__file__)), '../data/benchmark/'),os.path.join(os.path.dirname(os.path.abspath(__file__)), '../data/sanity-check-failures/'),os.path.join(os.path.dirname(os.path.abspath(__file__)), '../data/unsuccessful-reproductions/')]
    for BENCHMARK_DIR in BENCHMARK_DIRS:
        for file_name in os.listdir(BENCHMARK_DIR):
            if file_name.endswith('.json'):
                file_path = os.path.join(BENCHMARK_DIR, file_name)
                update_json_file(file_path, mapping)