# %%
import os
import json
import pandas as pd

import requests
GITHUB_TOKEN='github_pat_'

# Define the path to the folder containing the JSON files
BENCHMARK_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../data/benchmark/')

# List to store the JSON data
data = []

# Load all JSON files in the folder
for file_name in os.listdir(BENCHMARK_DIR):
    if file_name.endswith('.json'):
        file_path = os.path.join(BENCHMARK_DIR, file_name)
        with open(file_path, 'r') as file:
            json_data = json.load(file)
            data.append(json_data)

# %%
# Convert the list of dictionaries to a pandas DataFrame
df = pd.json_normalize(data)

# %%

original_search_condition = df['updatedDependency.githubCompareLink'] == "A GitHub repository could not be found for the updated dependency."

new_search_condition = df['updatedDependency.githubCompareLink'].str.contains("Relevant tags were not found", na=False)

# combined_search_condition = original_search_condition | new_search_condition
combined_search_condition = original_search_condition

filtered_df = df[combined_search_condition]

# %%

unique_combinations = filtered_df[['updatedDependency.dependencyGroupID', 'updatedDependency.dependencyArtifactID']].drop_duplicates()

# Sort by dependencyGroupID
unique_combinations = unique_combinations.sort_values(by='updatedDependency.dependencyGroupID')

outputs = []

for _, row in unique_combinations.iterrows():
    output = {
        "updatedDependency.dependencyGroupID": row['updatedDependency.dependencyGroupID'],
        "updatedDependency.dependencyArtifactID": row['updatedDependency.dependencyArtifactID'],
        "githubRepoLink": ""  # Add the corresponding GitHub repo link if available
    }
    outputs.append(output)

print(json.dumps(outputs, indent=2))

# %%
with open('manual_repo_mapping.json', 'r') as file:
  mapping = json.load(file)


# %%
def get_tags(repo_api_url):
    tags = []
    page = 1
    if len(repo_api_url) < 1:
        return tags
    while True:
        
        tags_url = f"{repo_api_url}/tags?page={page}&per_page=100"
        response = requests.get(tags_url, headers={'Authorization': f'token {GITHUB_TOKEN}'})
        if response.status_code != 200:
            break

        page_tags = response.json()
        if not page_tags:
            break
        tags.extend([tag['name'] for tag in page_tags])
        page += 1
    return tags

def construct_compare_url(repo_url, prev_version, new_version):
    # check if url returns 200
    print("Construct", repo_url, prev_version, new_version)
    formatted_repo_url = f"{repo_url}/compare/{prev_version}...{new_version}"
    req = requests.get(formatted_repo_url)
    if req.status_code == 200:
        return formatted_repo_url
    else:
        return False

def is_in_tags(tags, version):
    return any(version in tag for tag in tags)

def find_version_in_tags(tags, version):
    for tag in tags:
        if version in tag:
            return tag
    return None

def update_json_file(file_path, mapping):
    with open(file_path, 'r') as file:
        entry = json.load(file)

    updated = False


    updatedDependency = entry.get('updatedDependency')
    group_id = updatedDependency.get('dependencyGroupID')
    artifact_id = updatedDependency.get('dependencyArtifactID')
    prev_version = updatedDependency.get('previousVersion')
    new_version = updatedDependency.get('newVersion')
    print(group_id, artifact_id, prev_version, new_version)

    # Find the corresponding repo link
    repo_info = next((item for item in mapping if item["updatedDependency.dependencyGroupID"] == group_id and (item["updatedDependency.dependencyArtifactID"] == artifact_id or item["updatedDependency.dependencyArtifactID"] == "*")), None)

    if repo_info:
        repo_api_url = repo_info["githubRepoLink"].replace('github.com', 'api.github.com/repos')
        tags = get_tags(repo_api_url)

        relevant_tags_not_found_message = f"Relevant tags were not found in the GitHub repository {(repo_info['githubRepoLink']).replace('https://github.com/', '')} for the updated dependency."

        prev_tag = find_version_in_tags(tags, prev_version)
        new_tag = find_version_in_tags(tags, new_version)

        if prev_tag and new_tag:
            compare_url = construct_compare_url(repo_info["githubRepoLink"], prev_tag, new_tag)
            if compare_url:
                entry['updatedDependency']['githubCompareLink'] = compare_url
            else:
                entry['updatedDependency']['githubCompareLink'] = relevant_tags_not_found_message
        else:
            entry['updatedDependency']['githubCompareLink'] = relevant_tags_not_found_message
        updated = True
    else:
        entry['updatedDependency']['githubCompareLink'] = "Repository information not found in mapping."

    if updated:
        with open(file_path, 'w') as file:
            file_text = json.dumps(entry, indent=2)
            file.write(file_text.replace('": ', '" : '))

def process_benchmark_directory(directory, filtered_df, mapping):
    for idx, row in filtered_df.iterrows():
        breaking_commit = row['breakingCommit']
        file_path = os.path.join(directory, f"{breaking_commit}.json")
        if os.path.isfile(file_path):
            update_json_file(file_path, mapping)

# Assuming 'filtered_df' and 'mapping' are already defined
process_benchmark_directory(BENCHMARK_DIR, filtered_df, mapping)

# %%
# load every file from data/benchmark and save it with the weird Jackson indentation
for file_name in os.listdir(BENCHMARK_DIR):
    if file_name.endswith('.json'):
        file_path = os.path.join(BENCHMARK_DIR, file_name)
        with open(file_path, 'r') as file:
            entry = json.load(file)
        if 'Relevant tags were not found in the' in entry['updatedDependency']['githubCompareLink']:
            entry['updatedDependency']['githubCompareLink'].replace('https://github.com/', '')
        with open(file_path, 'w') as file:
            file_text = json.dumps(entry, indent=2)
            file.write(file_text.replace('": ', '" : '))



# %%
