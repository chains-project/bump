# BUMP Breaking Updates

## Overview 
Bump is a benchmark of breaking dependency updates. It can be downloaded from [`Zenodo`](https://zenodo.org/records/10041883).
A breaking updates is defined as:
a pair of commits for a Java project, which we designate as the pre-commit and the breaking-commit. 
When we build the project with the pre-commit, compilation and test execution are successful, 
while the build of the breaking-commit fails. 
Each breaking-commit is a one-line change in the Maven pom file.

This definition matches common dependency "bumps" as performed by bots such as 
Dependabot and Renovate, but also corresponds to an update made by a human developer.

## Download BUMP 

All breaking updates in Bump are stored within Docker images. They can be downloaded from [`Zenodo`](https://zenodo.org/records/10041883).
<br>To easily download the Zenodo tar file and load the associated Docker images use the following commands:
<br> ⚠️ **Warning:** You need a minimum of 250 GB of free disk space to load the images.

```bash
$ wget https://zenodo.org/records/10041883/files/bump.tar.gz
$ docker load -i bump.tar.gz # this loads 1142 images
$ docker images | wc -l
1142
# running a breaking commit
# docker run ghcr.io/chains-project/breaking-updates:<tag>{-pre,-breaking}
$ docker run ghcr.io/chains-project/breaking-updates:5769bdad76925da568294cb8a40e7d4469699ac3-breaking
```

## Data format 
Gathered data can be found as JSON files in the [`data`](/data) folder.
There are 3 sub-folders inside the data folder.
  * [`benchmark`](/data/benchmark) : contains the successfully reproduced breaking dependency updates.
  * [`in-progress-reproductions`](/data/in-progress-reproductions) : contains the potential breaking updates which have not yet been reproduced.
  * [`sanity-check-failures`](/data/sanity-check-failures) : contains the data that are removed after the sanity-check procedure.
  * [`unsuccessful-reproductions`](/data/unsuccessful-reproductions) : contains the data regarding unsuccessful reproduction attempts.
Each file inside these folders is named according to the SHA of the (potential) breaking commit.

The JSON files in our benchmark of breaking dependency updates have the following JSON data format.
```json
{
    "url": "<github pr url>",
    "project": "<github_project>",
    "projectOrganisation": "<github_project_organisation>",
    "breakingCommit": "<sha>",
    "prAuthor": "{human|bot}",
    "preCommitAuthor": "{human|bot}",
    "breakingCommitAuthor": "{human|bot}",
    "updatedDependency": {
      "dependencyGroupID": "<group id>",
      "dependencyArtifactID": "<artifact id>",
      "previousVersion": "<label indicating the previous version of the dependency>",
      "newVersion": "<label indicating the new version of the dependency>",
      "dependencyScope": "{compile|provided|runtime|system|import}",
      "versionUpdateType": "{major|minor|patch|other}",
      "githubCompareLink": "<the github comparison link for the previous and breaking tag releases of the updated dependency if it exists>",
      "mavenSourceLinkPre": "<maven source jar link for the previous release of the updated dependency if it exists>",
      "mavenSourceLinkBreaking": "<maven source jar link for the breaking release of the updated dependency if it exists>",
      "updatedFileType": "{pom|jar}",
      "dependencySection" : "{dependencies|dependencyManagement|buildPlugins|buildPluginManagement|profileBuildPlugins}"
  },
    "preCommitReproductionCommand": "<the command to compile and run tests without the breaking update commit>",
    "breakingUpdateReproductionCommand": "<the command to compile and run tests with the breaking update commit>",
    "javaVersionUsedForReproduction": "<the java version version used for reproduction>",
    "failureCategory": "<the category of the root cause of the reproduction failure>"
}
```

## Workflow
The data gathering workflow is as follows: 
* Stage 1 : Collect Java projects which meet the following criteria.
    * builds with Maven,
    * has at least 100 commits on the default branch, 
    * created in the last 10 years, 
    * has at least 3 contributors, 
    * has at least 10 stars.
* Stage 2 : Identify the breaking updates. 
* Stage 3 : Reproduce the failure locally under the assumptions documented below. 
  * Assumptions:
    * We run Linux (kernel version and distribution to be documented)
    * We use Maven version 3.8.6
    * We run OpenJDK
    * As a starting point, we use Java 11
  * The reproduction can result in different successful outcomes based on the Maven goal where the failure happens. For example,
    * The compilation step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "COMPILATION_FAILURE".
    * The test step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "TEST_FAILURE".
    * The project build fails _after_ the dependency is updated due to unresolved dependencies, but not before.
      This is a successful reproduction corresponding to the label "DEPENDENCY_RESOLUTION_FAILURE".
    * The project build fails _after_ the dependency is updated due to enforcer rules violations, but not before.
      This is a successful reproduction corresponding to the label "ENFORCER_FAILURE".
    * The project build fails _after_ the dependency is updated when executing the plugin dependency-lock-maven-plugin, but not before.
      This is a successful reproduction corresponding to the label "DEPENDENCY_LOCK_FAILURE".
    * The project build fails _after_ the dependency is updated due to the activation of the `failOnWarning` option in the configuration file.
      This is a successful reproduction corresponding to the label "WERROR_FAILURE".
* Stage 4 : Build two Docker images for each successfully reproduced breaking update, 
            and isolate all environment / network requests by downloading them.
            After stage 4, by running the preCommitReproductionCommand, and the breakingUpdateReproductionCommand, 
            the successful build of the previous commit and the failing build of the breaking commit can be reproduced. 

## Tools

### The BreakingUpdateMiner
In order to gather breaking dependency updates from GitHub, a tool called the 
BreakingUpdateMiner is available.  
You can build this tool locally using `mvn package` with Java 17.
You can then run the tool and print usage information with the command:
```bash
java -jar target/BreakingUpdateMiner.jar --help 
```

### The BreakingUpdateReproducer
In order to perform local reproduction once potential breaking uppdates have been found by the miner,
a tool called the BreakingUpdateReproducer is available.
You can build this tool locally using `mvn package` with Java 17.
You can then run the tool and print usage information with the command:
```bash
java -jar target/BreakingUpdateReproducer.jar --help 
```

