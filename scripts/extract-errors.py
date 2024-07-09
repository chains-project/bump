# walk 

import os
from pathlib import Path
import argilla as rg
import pandas as pd

DATASET_NAME = "maven-faults-labels"

rg.init(
    api_url="https://LukvonStrom-annotate-bumps-errors.hf.space",
    api_key='owner.apikey.12345678910',
    workspace="admin")
#owner.password.1234567891011

# Create a dataset in Argilla
# dataset_name = "build_failure_logs"
# rg.create_dataset(dataset_name)
dataset = rg.FeedbackDataset.for_text_classification(
    labels=["JDK_UPGRADE", "DEPENDENCY_BREAKING_API_CHANGE", "DEPENDENCY_VERSION_MISMATCH", "OTHER"],
    multi_label=False,
    use_markdown=True,
    guidelines=None,
    metadata_properties=[
                     rg.TermsMetadataProperty(
                         name="commit_hash",
                     ),
                     ],
    vectors_settings=None,
)


root_dir = Path(__file__).resolve().parent.parent
logs_dir = root_dir / 'reproductionLogs/successfulReproductionLogs'


# "licenseInfo" : "NOASSERTION"
# "licenseInfo" : "No license found"


merged_df = pd.read_parquet(root_dir / 'RQData/merged_data.parquet')

data = []
for commit_file in os.listdir(logs_dir):
    # Skip if license is wrong
    commit_hash = commit_file.replace('.log', '')
    found_entry = merged_df[merged_df['breakingCommit'] == commit_hash]
    
    if not found_entry.empty:
        license_info = found_entry['licenseInfo'].iloc[0]
        if license_info == "NOASSERTION":
            print(f"Skipping {commit_hash} because of NOASSERTION")
            continue
        if license_info == "No license found":
            print(f"Skipping {commit_hash} because of No license found")
            continue
        

    with open(logs_dir / commit_file, 'r', encoding="utf-8") as file:
        log_content = file.readlines()
        commit_hash = commit_file.replace('.log', '')
        error_lines = []
        RECORDING_ERRORS = False

        for line in log_content:
            if "[INFO] BUILD FAILURE" in line:
                RECORDING_ERRORS = True
            if RECORDING_ERRORS:
                error_lines.append(line.strip())

        if error_lines:
            error_text = '\n'.join(error_lines)

            # Heuristic for pre-labeling errors
            if "Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin" in error_text:
                pre_label = "DEPENDENCY_BREAKING_API_CHANGE"
            elif "Failed to execute goal se.vandmo:dependency-lock-maven-plugin" in error_text: 
                pre_label = "DEPENDENCY_VERSION_MISMATCH"
            elif "Some Enforcer rules have failed" in error_text:
                pre_label = "DEPENDENCY_VERSION_MISMATCH"
            elif "org.apache.maven.enforcer.rules" in error_text:
                pre_label = "DEPENDENCY_VERSION_MISMATCH"
            elif "requires Jenkins":
                continue
            else:
                pre_label = "OTHER"  # Default label

            # data.append({'commit_hash': commit_hash, 'error': error_text})

            # Log to Argilla
            record = rg.FeedbackRecord(
                fields={
                    "text": error_text,
                },
                metadata={'commit_hash': commit_hash},
                suggestions = [
                    {
                        "question_name": "label",
                        "value": pre_label,
                    }
                ]
            )

            # Log the record to Argilla
            dataset.add_records([record])
# df = pd.DataFrame(data)

dataset.push_to_argilla(name=DATASET_NAME, workspace="admin")

