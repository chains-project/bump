# Breaking Updates

## Overview 
A benchmark of breaking dependency updates. 
These breaking updates are defined as:
A pair of commits for a Java project, which we designate as the pre-commit and the breaking-commit. 
When we build the project with the pre-commit, compilation and test execution are successful, 
and the build with the breaking-commit fails. 
In our benchmark each breaking-commit is a one-line change in the pom file.

This definition matches common dependency "bumps" as performed by bots such as 
Dependabot and Renovate, but could also correspond to an update made by a human developer.

## Data format 
Gathered data can be found as JSON files in the [`data`](/data) folder.
There are 3 sub-folders inside the data folder.
  * [`benchmark`](/data/benchmark) : contains the successfully reproduced breaking dependency updates.
  * [`not-reproduced-data`](/data/not-reproduced-data) : contains the potential breaking updates which have not yet been reproduced.
  * [`unsuccessful-reproductions`](/data/unsuccessful-reproductions) : contains the data regarding unsuccessful reproduction attempts.
Each file inside these folders is named according to the SHA of the (potential) breaking commit.

The JSON files in our benchmark of breaking dependency updates have the following JSON data format.
```json
{
    "url": "<github pr url>",
    "project": "<github_project>",
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
      "updatedFileType": "{pom|jar}"
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
  * The reproduction can result in 5 different successful outcomes:
    * The project build fails _after_ the dependency is updated due to unresolved dependencies, but not before.
      This is a successful reproduction corresponding to the label "DEPENDENCY_RESOLUTION_FAILURE".
    * The project build fails _after_ the dependency is updated due to maven enforcer plugin errors, but not before.
      This is a successful reproduction corresponding to the label "MAVEN_ENFORCER_FAILURE".
    * The compilation step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "COMPILATION_FAILURE".
    * The test step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "TEST_FAILURE".
    * The project build fails _after_ the dependency is updated due to an unknown error which cannot be categorized
      into above other failure types.
      This is a successful reproduction corresponding to the label "UNKNOWN_FAILURE".
* Stage 4 : Build two Docker images for each successfully reproduced breaking update, 
            and isolate all environment / network requests by downloading them.
            After stage 4, by running the preCommitReproductionCommand, and the breakingUpdateReproductionCommand, 
            the successful build of the previous commit and the failing build of the breaking commit can be reproduced. 

## Tools

### The BreakingUpdateMiner
In order to gather breaking dependency updates from GitHub, a tool called the 
BreakingUpdateMiner is available.  
You can build this tool locally using `mvn package`.
You can then run the tool and print usage information with the command:
```bash
java -jar target/BreakingUpdateMiner.jar --help 
```

### The BreakingUpdateReproducer
In order to perform local reproduction once potential breaking uppdates have been found by the miner,
a tool called the BreakingUpdateReproducer is available.
You can build this tool locally using `mvn package`.
You can then run the tool and print usage information with the command:
```bash
java -jar target/BreakingUpdateReproducer.jar --help 
```

## Stats
As of Aug 10 2023:
  * The benchmark consists of 331 reproducible breaking updates.
    - Of these breaking updates, 124 (37.46%) fail compilation with the updated dependency.
    - 95 (28.70%) fail tests with the updated dependency.
    - 1 (0.30%) have dependency resolution failures with the updated dependency.
    - 55 (16.62%) fail after updating the dependency due to maven enforcer failures.
    - 56 (16.92%) fail due to unknown failures after updating the dependency.
  * Overall, reproduction has been attempted for 2754 breaking updates, and 2423 (87.98%) could not be locally reproduced.
  * For 0 potential breaking updates, reproduction has not been attempted yet.
