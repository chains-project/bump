# Maven 4 Failure Categories

---

## Fix Summary by Category

| Category | Count | Fix |
|---|---|---|
| PARENT_POM_UNRESOLVABLE | 54 | Add `-o` (offline) flag or ensure the non-central repo (e.g. Jenkins, Google) is reachable; or pin parent POM to a version available on Central |
| ENFORCER_SESSION_CAST | 48 | Upgrade `maven-enforcer-plugin` to `3.4.0+` |
| NETWORK_REPO_ERROR | 23 | Replace deprecated repo URLs (e.g. `maven.java.net` → `repo1.maven.org`); add repo mirrors or fix TLS certs in the build environment |
| PLUGIN_EXPRESSION_EVALUATOR_API | 22 | Upgrade `maven-enforcer-plugin` to `3.4.0+` (removes use of the deleted `PluginParameterExpressionEvaluator` constructor) |
| PLUGIN_GETGOALS_API | 18 | Upgrade `maven-shade-plugin` to `3.5.0+` (removes call to deleted `Plugin.getGoals()`) |
| DEPENDENCY_LOCK_MISMATCH | 13 | Re-generate the dependency lock file with Maven 4 (`mvn se.vandmo:dependency-lock-maven-plugin:update`) or upgrade the plugin |
| DUPLICATE_DEPENDENCY | 10 | Remove the duplicate entry from `<dependencyManagement>` in `pom.xml`, keeping only one version |
| PARENT_POM_CYCLE | 9 | Break the circular parent reference — usually caused by a BOM importing itself; restructure into a separate parent and BOM module |
| SESSION_CONTAINER_NULL | 7 | Upgrade the offending plugin (e.g. `exec-maven-plugin` to `3.2.0+`) which uses the new `MavenSession` API instead of `getContainer()` |
| JGITVER_EXTENSION | 6 | Move jgitver from plugin config to a Maven core extension in `.mvn/extensions.xml` |
| TAKARI_LIFECYCLE | 4 | Replace `takari-lifecycle-plugin` with standard `maven-compiler-plugin` + `maven-jar-plugin` (Takari is abandoned and incompatible with Maven 4) |
| OTHER | 5 | See per-case details below |

> The fixes are being investigated in https://github.com/chains-project/bump/issues/267.

---

## PARENT_POM_UNRESOLVABLE (54)

**Fix:** Add the missing repository to the build environment or run with `-o` (offline) to use the cached copy. For Jenkins plugins, ensure `https://repo.jenkins-ci.org/public/` is reachable. Long-term, upgrade to a parent POM version available on Maven Central.




- [f412c0f6f2bfd2a7aa3522b0f989f6b920a66a64/maven4.log](f412c0f6f2bfd2a7aa3522b0f989f6b920a66a64/maven4.log)
- [14270a2ff9e02717d50f77fe5473b0aaca7fc9db/maven4.log](14270a2ff9e02717d50f77fe5473b0aaca7fc9db/maven4.log)
- [759b010d87ad45c7aa6a4835890e666cfcaaec0e/maven4.log](759b010d87ad45c7aa6a4835890e666cfcaaec0e/maven4.log)
- [22ce3b0e5c6c447c3c094bd145e9a097237ac9a9/maven4.log](22ce3b0e5c6c447c3c094bd145e9a097237ac9a9/maven4.log)
- [8b1bc48bc378cd22999b9f7d495aa5b00b080770/maven4.log](8b1bc48bc378cd22999b9f7d495aa5b00b080770/maven4.log)
- [e40f76d1150d41821ccfd72e9dd3fabbc8763c1e/maven4.log](e40f76d1150d41821ccfd72e9dd3fabbc8763c1e/maven4.log)
- [44b14e90e4baa1b1971327f7167afb1b0f0dd8f0/maven4.log](44b14e90e4baa1b1971327f7167afb1b0f0dd8f0/maven4.log)
- [763d17f9291f3435b02d55a521ee204fbe3d0c7a/maven4.log](763d17f9291f3435b02d55a521ee204fbe3d0c7a/maven4.log)
- [26064aa3340526ef7924d3736cbf3c6e449a9b61/maven4.log](26064aa3340526ef7924d3736cbf3c6e449a9b61/maven4.log)
- [940214f7419c564431163c4599d24ce3152a2ca0/maven4.log](940214f7419c564431163c4599d24ce3152a2ca0/maven4.log)
- [26fd1cd7639b7deb7078df5e4cb05c6d463ad07a/maven4.log](26fd1cd7639b7deb7078df5e4cb05c6d463ad07a/maven4.log)
- [e074b52364dd631a2fa56b1290ed7572e23da29c/maven4.log](e074b52364dd631a2fa56b1290ed7572e23da29c/maven4.log)
- [4ea77102208edb25d3d7623a687538aba6f184e9/maven4.log](4ea77102208edb25d3d7623a687538aba6f184e9/maven4.log)
- [07e4b2894bc68cd3bb1892beaa13ec353564dcf1/maven4.log](07e4b2894bc68cd3bb1892beaa13ec353564dcf1/maven4.log)
- [8d55e2e454692c14deeffa58518698ad112602a0/maven4.log](8d55e2e454692c14deeffa58518698ad112602a0/maven4.log)
- [3d205ddac1e3db863609be19116865e4cfa6dfc9/maven4.log](3d205ddac1e3db863609be19116865e4cfa6dfc9/maven4.log)
- [6d68d927b01ceb567f1067a91afbc97b4c5a66ce/maven4.log](6d68d927b01ceb567f1067a91afbc97b4c5a66ce/maven4.log)
- [433ded52029eea2f7a8699f9d61f2e170c44c8ae/maven4.log](433ded52029eea2f7a8699f9d61f2e170c44c8ae/maven4.log)
- [5a0d2c907276ea5b31ef08786d01ed18689f5d00/maven4.log](5a0d2c907276ea5b31ef08786d01ed18689f5d00/maven4.log)
- [9fd4403e49fe380d2cb348172029c64f1d8f7ecb/maven4.log](9fd4403e49fe380d2cb348172029c64f1d8f7ecb/maven4.log)
- [52ba94ad488d562bce56eee92fb46c63fba6541b/maven4.log](52ba94ad488d562bce56eee92fb46c63fba6541b/maven4.log)
- [17cc92fc964c1aaf486ed815c60725b6f65714d2/maven4.log](17cc92fc964c1aaf486ed815c60725b6f65714d2/maven4.log)
- [71f0631e9cee4ea1f5829f6e127f474e9095fda5/maven4.log](71f0631e9cee4ea1f5829f6e127f474e9095fda5/maven4.log)
- [d445acc2204a18c99f5e62a1f641e15e75659249/maven4.log](d445acc2204a18c99f5e62a1f641e15e75659249/maven4.log)
- [50be65a0234e63fe463ce8f2348a7dd8e34e2a72/maven4.log](50be65a0234e63fe463ce8f2348a7dd8e34e2a72/maven4.log)
- [1d0c5406203e778b5e67534cdf72f4370fd6d301/maven4.log](1d0c5406203e778b5e67534cdf72f4370fd6d301/maven4.log)
- [3e2f981f08e75926838fb0fa6d96fb3efba33bc3/maven4.log](3e2f981f08e75926838fb0fa6d96fb3efba33bc3/maven4.log)
- [d70adda72af8cc1cfe1f52350a4664e09ad10b7a/maven4.log](d70adda72af8cc1cfe1f52350a4664e09ad10b7a/maven4.log)
- [fc969c97c1b5d8c4aa8255563eecc1ee1695cbd5/maven4.log](fc969c97c1b5d8c4aa8255563eecc1ee1695cbd5/maven4.log)
- [f6aa80590030f7109e6ae80b58fd32fd176308c4/maven4.log](f6aa80590030f7109e6ae80b58fd32fd176308c4/maven4.log)
- [fd955874338ec4b678416f6d1b62a8b1f499d9e3/maven4.log](fd955874338ec4b678416f6d1b62a8b1f499d9e3/maven4.log)
- [283c6ddc77ec4f7c52e297926cb8a6960a3f8ab6/maven4.log](283c6ddc77ec4f7c52e297926cb8a6960a3f8ab6/maven4.log)
- [6a96a3c8ab6523e9876ee49fdac32b494cdca4b3/maven4.log](6a96a3c8ab6523e9876ee49fdac32b494cdca4b3/maven4.log)
- [b349a544f5d004d5297879a0cfdb5f39e0a09110/maven4.log](b349a544f5d004d5297879a0cfdb5f39e0a09110/maven4.log)
- [ef830a40b026342754f923d1fa8ebb78fda1ca4d/maven4.log](ef830a40b026342754f923d1fa8ebb78fda1ca4d/maven4.log)
- [ccc3057b9cbfdc307d01be295c67d142187d197d/maven4.log](ccc3057b9cbfdc307d01be295c67d142187d197d/maven4.log)
- [7e8c62e2bb21097e563747184636cf8e8934ce98/maven4.log](7e8c62e2bb21097e563747184636cf8e8934ce98/maven4.log)
- [99a43573c3ea90d8b942627b8ea947933d229863/maven4.log](99a43573c3ea90d8b942627b8ea947933d229863/maven4.log)
- [18eff0121ded81b30af0924676407bfc663e6557/maven4.log](18eff0121ded81b30af0924676407bfc663e6557/maven4.log)
- [f9763c18c7e1fa54fb67dcf3935aa5106807aba9/maven4.log](f9763c18c7e1fa54fb67dcf3935aa5106807aba9/maven4.log)
- [06c5386831e97e94d9b9fd155d3ea4aa8711c4e7/maven4.log](06c5386831e97e94d9b9fd155d3ea4aa8711c4e7/maven4.log)
- [9ae83176a05822b8032bef50908c296dbfe88fde/maven4.log](9ae83176a05822b8032bef50908c296dbfe88fde/maven4.log)
- [add76d2702720eec58110eb133a1511af7455f97/maven4.log](add76d2702720eec58110eb133a1511af7455f97/maven4.log)
- [ca09cbc2092a1083d1e640adfec7d6a188e5e935/maven4.log](ca09cbc2092a1083d1e640adfec7d6a188e5e935/maven4.log)
- [3a4a2b11483689ca3e99e92785a7b27c56d072b8/maven4.log](3a4a2b11483689ca3e99e92785a7b27c56d072b8/maven4.log)
- [441f7f07d9265cc1d4c4f369ee6524973d9c6e17/maven4.log](441f7f07d9265cc1d4c4f369ee6524973d9c6e17/maven4.log)
- [8ab7a7214f9ac1d130b416fae7280cfda533a54f/maven4.log](8ab7a7214f9ac1d130b416fae7280cfda533a54f/maven4.log)
- [2ea4f3a642abcd460c303713f0de8cd803cff378/maven4.log](2ea4f3a642abcd460c303713f0de8cd803cff378/maven4.log)
- [6661429a5a0e998cf17daa45d8c026bdfaf9bc3f/maven4.log](6661429a5a0e998cf17daa45d8c026bdfaf9bc3f/maven4.log)
- [82ce7330b4b2fec4abc0858fabd21c1254ece439/maven4.log](82ce7330b4b2fec4abc0858fabd21c1254ece439/maven4.log)
- [8dda40abd229427ce0651f55a787015899c95570/maven4.log](8dda40abd229427ce0651f55a787015899c95570/maven4.log)
- [5aa75fa04b54eb9157894e85aa0f6ea4bfdac677/maven4.log](5aa75fa04b54eb9157894e85aa0f6ea4bfdac677/maven4.log)
- [5836280b1a73c5b7fe17e3d7a369c025bcd84053/maven4.log](5836280b1a73c5b7fe17e3d7a369c025bcd84053/maven4.log)
- [ad68349ac058caf14e021e518055b67aca19b663/maven4.log](ad68349ac058caf14e021e518055b67aca19b663/maven4.log)

## ENFORCER_SESSION_CAST (48)

**Fix:** Upgrade `maven-enforcer-plugin` to `3.4.0+`. The old plugin casts the Aether session to `DefaultRepositorySystemSession` which no longer works in Maven 4 — the new version uses the updated API.


- [959774bbbc3839e33c02a564f75cb28de5d308e2/maven4.log](959774bbbc3839e33c02a564f75cb28de5d308e2/maven4.log)
- [735c2ce3d22b03d5781de4584a6b5f5dff8f9a05/maven4.log](735c2ce3d22b03d5781de4584a6b5f5dff8f9a05/maven4.log)
- [1cf2cb24c3532551856d5edd85148f978e11a63c/maven4.log](1cf2cb24c3532551856d5edd85148f978e11a63c/maven4.log)
- [efd982c815eb9e82476b44386a6e3572d6a807c8/maven4.log](efd982c815eb9e82476b44386a6e3572d6a807c8/maven4.log)
- [1750bac074c30b06ae5cdff7a25db401a4f45de2/maven4.log](1750bac074c30b06ae5cdff7a25db401a4f45de2/maven4.log)
- [0c748afce24c983e8f330bc2435837c70b0fcde6/maven4.log](0c748afce24c983e8f330bc2435837c70b0fcde6/maven4.log)
- [e3b288f3a1507baf36451c4844d6f360b472e3d1/maven4.log](e3b288f3a1507baf36451c4844d6f360b472e3d1/maven4.log)
- [50829a2e5d572a679b39716c3a406f3853e50ce2/maven4.log](50829a2e5d572a679b39716c3a406f3853e50ce2/maven4.log)
- [8b372dfe79625ff585c5b0392913b6b152cc7c22/maven4.log](8b372dfe79625ff585c5b0392913b6b152cc7c22/maven4.log)
- [cd5bb39f43e4570b875027073da3d4e43349ead1/maven4.log](cd5bb39f43e4570b875027073da3d4e43349ead1/maven4.log)
- [78252d13ff355a0205fff6fb7c19791412bc6478/maven4.log](78252d13ff355a0205fff6fb7c19791412bc6478/maven4.log)
- [19249139eb66616953c389242c22edde60c4ac2f/maven4.log](19249139eb66616953c389242c22edde60c4ac2f/maven4.log)
- [d154a6b3665305fda43a95a66c8df415d1d4c041/maven4.log](d154a6b3665305fda43a95a66c8df415d1d4c041/maven4.log)
- [923528a3dd1bcdba21490f7583c80a67d01285b2/maven4.log](923528a3dd1bcdba21490f7583c80a67d01285b2/maven4.log)
- [c5c8dfee350e07c014506dfdfead5556d0bc6a2a/maven4.log](c5c8dfee350e07c014506dfdfead5556d0bc6a2a/maven4.log)
- [94cea45b797ef15ab6dcfd20157d7b2f8d0f3cde/maven4.log](94cea45b797ef15ab6dcfd20157d7b2f8d0f3cde/maven4.log)
- [12830c4f63bd8edf3621db703b822a2149233e85/maven4.log](12830c4f63bd8edf3621db703b822a2149233e85/maven4.log)
- [0ec1ab7e205e0ea1bafc0e0ab7be056716dd560b/maven4.log](0ec1ab7e205e0ea1bafc0e0ab7be056716dd560b/maven4.log)
- [3c68e04f6a8ab2977a5c8fea5e2aa5373b0df742/maven4.log](3c68e04f6a8ab2977a5c8fea5e2aa5373b0df742/maven4.log)
- [b5d920ebc2b2ada185467c989310e2f78806bc95/maven4.log](b5d920ebc2b2ada185467c989310e2f78806bc95/maven4.log)
- [c3132b266fa9bb6a8dbfbc548cd2ca2b99d8d8af/maven4.log](c3132b266fa9bb6a8dbfbc548cd2ca2b99d8d8af/maven4.log)
- [0968864d08e0fce1e5e1caaf89afddd2cc1b2569/maven4.log](0968864d08e0fce1e5e1caaf89afddd2cc1b2569/maven4.log)
- [c46623986f77fdd0a206e3be2fae7e283b157be3/maven4.log](c46623986f77fdd0a206e3be2fae7e283b157be3/maven4.log)
- [aef811ea00446dc800e8fba6cfc15d6d297793ea/maven4.log](aef811ea00446dc800e8fba6cfc15d6d297793ea/maven4.log)
- [2c957083a17edc918a21f20d2838cf7c21719700/maven4.log](2c957083a17edc918a21f20d2838cf7c21719700/maven4.log)
- [6b2f30ebeacbc54b3b86971b2f2513c7ba9cf872/maven4.log](6b2f30ebeacbc54b3b86971b2f2513c7ba9cf872/maven4.log)
- [5effea24fcd960f5659130912daf5e1d6319c9f4/maven4.log](5effea24fcd960f5659130912daf5e1d6319c9f4/maven4.log)
- [5eb6dbe8e9c3dd1ba5e3f52c15608d28fc85e3a6/maven4.log](5eb6dbe8e9c3dd1ba5e3f52c15608d28fc85e3a6/maven4.log)
- [64b8a013098fc450e0e6ef0f38d1acd8cfbb99c1/maven4.log](64b8a013098fc450e0e6ef0f38d1acd8cfbb99c1/maven4.log)
- [c3d0aba8fabeb8026b73bdc1b891f64271cd06e7/maven4.log](c3d0aba8fabeb8026b73bdc1b891f64271cd06e7/maven4.log)
- [f1dfdcd3774cc7ad8900e232607d1519085e48d7/maven4.log](f1dfdcd3774cc7ad8900e232607d1519085e48d7/maven4.log)
- [b736ca80eab43feae1b3224f8402e0d2420fd035/maven4.log](b736ca80eab43feae1b3224f8402e0d2420fd035/maven4.log)
- [dc7825479563597d41c2b2fef1540a29d8b8ab36/maven4.log](dc7825479563597d41c2b2fef1540a29d8b8ab36/maven4.log)
- [cd1b75ae5b6d703c29d9e2bd5a0ed230a129711a/maven4.log](cd1b75ae5b6d703c29d9e2bd5a0ed230a129711a/maven4.log)
- [e0f2562e849012538cd30e2730c82991983f4eba/maven4.log](e0f2562e849012538cd30e2730c82991983f4eba/maven4.log)
- [a48e9cf77fbae6f1af8e2c7ad31ab3f857d4fd23/maven4.log](a48e9cf77fbae6f1af8e2c7ad31ab3f857d4fd23/maven4.log)
- [700fd2428406fdaa4aacf00feab0cbbe634d70c2/maven4.log](700fd2428406fdaa4aacf00feab0cbbe634d70c2/maven4.log)
- [7731ee05ffba63b17fe0534702ccb56f286ffe7d/maven4.log](7731ee05ffba63b17fe0534702ccb56f286ffe7d/maven4.log)
- [c9cc7d936dda55c66b41411ba608549061914806/maven4.log](c9cc7d936dda55c66b41411ba608549061914806/maven4.log)
- [99ea58c42fe0aa521e6c62d04258f155b5abe26e/maven4.log](99ea58c42fe0aa521e6c62d04258f155b5abe26e/maven4.log)
- [f5fe6b784898301536f8733eba0ce43109488f5c/maven4.log](f5fe6b784898301536f8733eba0ce43109488f5c/maven4.log)
- [87fbea62fa6867b8288295b80718c51981ae340a/maven4.log](87fbea62fa6867b8288295b80718c51981ae340a/maven4.log)
- [3b7f524a09d711bc5af151086de48db940455e6a/maven4.log](3b7f524a09d711bc5af151086de48db940455e6a/maven4.log)
- [0c9a9c80287e739424508b4afd3e7b73697733ae/maven4.log](0c9a9c80287e739424508b4afd3e7b73697733ae/maven4.log)
- [e59bf2313ed8a7a196c271ba1c9ea0e5a4b66f24/maven4.log](e59bf2313ed8a7a196c271ba1c9ea0e5a4b66f24/maven4.log)
- [8f91818349e823e5d865eb3ebdf561da7ae1cfd8/maven4.log](8f91818349e823e5d865eb3ebdf561da7ae1cfd8/maven4.log)
- [806daaaaa907f679319f360c2ce99d5da5f7b5b5/maven4.log](806daaaaa907f679319f360c2ce99d5da5f7b5b5/maven4.log)
- [e259f250790e47143d9772ac3b483edf7a963527/maven4.log](e259f250790e47143d9772ac3b483edf7a963527/maven4.log)

## NETWORK_REPO_ERROR (23)

**Fix:** Replace deprecated repository URLs (e.g. `maven.java.net` → `https://repo1.maven.org/maven2`). For TLS/PKIX failures, update the JDK CA certificates in the build environment. Add mirrors in `settings.xml` for unreliable repos.


- [3572a1ecc0154c61e05505aed56055b9c5e539a6/maven4.log](3572a1ecc0154c61e05505aed56055b9c5e539a6/maven4.log)
- [067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9/maven4.log](067f5d2c81ff87c90755f4ed48f62eb5faa8ecf9/maven4.log)
- [5743793a5d54e43d9acf27e46fdb3a257ee0196f/maven4.log](5743793a5d54e43d9acf27e46fdb3a257ee0196f/maven4.log)
- [81fdd148772078d8d5bc07f3d0fae2116825561a/maven4.log](81fdd148772078d8d5bc07f3d0fae2116825561a/maven4.log)
- [aa14451c6f218af9c08e846345d83259eb7d46a8/maven4.log](aa14451c6f218af9c08e846345d83259eb7d46a8/maven4.log)
- [61e96bfe3a32d6ef2e5d7912a518c78bd5474e74/maven4.log](61e96bfe3a32d6ef2e5d7912a518c78bd5474e74/maven4.log)
- [165381d26b2c3d2278fde88c16f95807506451fe/maven4.log](165381d26b2c3d2278fde88c16f95807506451fe/maven4.log)
- [1629113f03956a230738c47397c33f8ba2d11341/maven4.log](1629113f03956a230738c47397c33f8ba2d11341/maven4.log)
- [de20387b7a373cf20daa590247a1b65876ebca38/maven4.log](de20387b7a373cf20daa590247a1b65876ebca38/maven4.log)
- [c1fc16b4fe9dfdfa16ce7248fccad0e7d994094d/maven4.log](c1fc16b4fe9dfdfa16ce7248fccad0e7d994094d/maven4.log)
- [ab70529b2edf0a0b3f672278e191dc207d1b8711/maven4.log](ab70529b2edf0a0b3f672278e191dc207d1b8711/maven4.log)
- [5476e69fca14862d862805790b73574e1de58dd2/maven4.log](5476e69fca14862d862805790b73574e1de58dd2/maven4.log)
- [832e0f184efdad0fcf15d14cb7af5e30239ff454/maven4.log](832e0f184efdad0fcf15d14cb7af5e30239ff454/maven4.log)
- [07ff1a34661db6c7f0ca03156ff5d8936b5123f5/maven4.log](07ff1a34661db6c7f0ca03156ff5d8936b5123f5/maven4.log)
- [db4b37b7cf6b0fe74565f15a03518ba68d4ce1ff/maven4.log](db4b37b7cf6b0fe74565f15a03518ba68d4ce1ff/maven4.log)
- [0cdcc1f1319311f383676a89808c9b8eb190145c/maven4.log](0cdcc1f1319311f383676a89808c9b8eb190145c/maven4.log)
- [433fbc0ee1192ca4aa69f337fd3b530ec94906e9/maven4.log](433fbc0ee1192ca4aa69f337fd3b530ec94906e9/maven4.log)
- [867e69e208ff59d1f8baae7ed41d3e163a51bc65/maven4.log](867e69e208ff59d1f8baae7ed41d3e163a51bc65/maven4.log)
- [e47fd8edc5227b6852ebd2466dd89049c9907b80/maven4.log](e47fd8edc5227b6852ebd2466dd89049c9907b80/maven4.log)
- [1e1de78344a89be66d2e78f7adb07a479f6677eb/maven4.log](1e1de78344a89be66d2e78f7adb07a479f6677eb/maven4.log)
- [b9f3ed467fa1dddb9e315325f5411349378d467c/maven4.log](b9f3ed467fa1dddb9e315325f5411349378d467c/maven4.log)
- [5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/maven4.log](5b90c67ef2d2ebb296534ea2ce8d8955cf6854c7/maven4.log)
- [bc209e43001af17218a6f401d0ea884d814790d1/maven4.log](bc209e43001af17218a6f401d0ea884d814790d1/maven4.log)

## PLUGIN_EXPRESSION_EVALUATOR_API (22)

**Fix:** Upgrade `maven-enforcer-plugin` to `3.4.0+`. Versions ≤ 3.1.x use a 6-argument `PluginParameterExpressionEvaluator` constructor that was removed from Maven 4's core API.


- [2f9d75d4b01cd18afcb4676d134142882f9e0034/maven4.log](2f9d75d4b01cd18afcb4676d134142882f9e0034/maven4.log)
- [d4aade950ffc07b63a0140e43ecf930a2c8aec43/maven4.log](d4aade950ffc07b63a0140e43ecf930a2c8aec43/maven4.log)
- [a67f1530f9413375ec0c2478cc3c01f67bed306f/maven4.log](a67f1530f9413375ec0c2478cc3c01f67bed306f/maven4.log)
- [7c90c61e90e9936d8a7e355de8900214c759cb61/maven4.log](7c90c61e90e9936d8a7e355de8900214c759cb61/maven4.log)
- [42a220bd546d293886df0d5e3892cc3ff82f1091/maven4.log](42a220bd546d293886df0d5e3892cc3ff82f1091/maven4.log)
- [aa7bdc45bb47197d959ceae62538a100a01a4d98/maven4.log](aa7bdc45bb47197d959ceae62538a100a01a4d98/maven4.log)
- [ed7fbdd75abc666d9d5a2794e9392ed33e75de9b/maven4.log](ed7fbdd75abc666d9d5a2794e9392ed33e75de9b/maven4.log)
- [19e20b0d69cb6dadbc54313cb6c6e5f70670ac93/maven4.log](19e20b0d69cb6dadbc54313cb6c6e5f70670ac93/maven4.log)
- [c4950c79dfe902dae8991ff722216c7ba787bf32/maven4.log](c4950c79dfe902dae8991ff722216c7ba787bf32/maven4.log)
- [0ee8b9376b967938e8efd89a0959214a30d1b3fb/maven4.log](0ee8b9376b967938e8efd89a0959214a30d1b3fb/maven4.log)
- [12684ee6cfd293b27c08495f97900bcd849b452c/maven4.log](12684ee6cfd293b27c08495f97900bcd849b452c/maven4.log)
- [ff8b5b61548d50cf60b77784a181e917cb35033b/maven4.log](ff8b5b61548d50cf60b77784a181e917cb35033b/maven4.log)
- [9069046236a07524578ff81b32ff92f34c59553d/maven4.log](9069046236a07524578ff81b32ff92f34c59553d/maven4.log)
- [28be199c825d419957bc753a9519e8e9ecc6a08e/maven4.log](28be199c825d419957bc753a9519e8e9ecc6a08e/maven4.log)
- [4631885da3cfd6601de5d24133fa3828a590ca9e/maven4.log](4631885da3cfd6601de5d24133fa3828a590ca9e/maven4.log)
- [2ad5b0e1f3512246c9de99259c35af15df677c77/maven4.log](2ad5b0e1f3512246c9de99259c35af15df677c77/maven4.log)
- [70e13f6bdb7de7f8eda9f174a5616284f2157ea7/maven4.log](70e13f6bdb7de7f8eda9f174a5616284f2157ea7/maven4.log)
- [8b057977647445aade80627a06bd65867f64b948/maven4.log](8b057977647445aade80627a06bd65867f64b948/maven4.log)
- [1053033eef680f0199bf25ec6e3db52cc13ef3da/maven4.log](1053033eef680f0199bf25ec6e3db52cc13ef3da/maven4.log)
- [c7c9590a206d4fb77dd05b9df391d888e6181667/maven4.log](c7c9590a206d4fb77dd05b9df391d888e6181667/maven4.log)
- [3dfb94445951d200d7db5327cae1c4d8b4db89b4/maven4.log](3dfb94445951d200d7db5327cae1c4d8b4db89b4/maven4.log)
- [5275fc9ef87a6d411b719d78c681b81ac914798b/maven4.log](5275fc9ef87a6d411b719d78c681b81ac914798b/maven4.log)

## PLUGIN_GETGOALS_API (18)

**Fix:** Upgrade `maven-shade-plugin` to `3.5.0+`. Versions 3.2.x–3.4.x call `Plugin.getGoals()` which was removed from Maven 4's model API.


- [c5db2b610e30061ea559e10cbccffb055d345d5e/maven4.log](c5db2b610e30061ea559e10cbccffb055d345d5e/maven4.log)
- [fcac4926cf5adb0de62abda8c386056bcc516f70/maven4.log](fcac4926cf5adb0de62abda8c386056bcc516f70/maven4.log)
- [d8e0311e6203d6b15c187e8536ea910b1bbc362f/maven4.log](d8e0311e6203d6b15c187e8536ea910b1bbc362f/maven4.log)
- [c923de11176e4fee34fa01bdb1cb1c90861fd0cf/maven4.log](c923de11176e4fee34fa01bdb1cb1c90861fd0cf/maven4.log)
- [919c808279d233d4babd9b2321606620786febaa/maven4.log](919c808279d233d4babd9b2321606620786febaa/maven4.log)
- [90ffd2cd31edecf778d14d0015da9ceab7e53081/maven4.log](90ffd2cd31edecf778d14d0015da9ceab7e53081/maven4.log)
- [a80dac86d1caa3958c45c036d93a7d9231d88fbf/maven4.log](a80dac86d1caa3958c45c036d93a7d9231d88fbf/maven4.log)
- [88a20ece4db960e35fbfa39fcb40e61daceb15b1/maven4.log](88a20ece4db960e35fbfa39fcb40e61daceb15b1/maven4.log)
- [1820a966ae02ad8df44d0a0106cba65ceaf3aa95/maven4.log](1820a966ae02ad8df44d0a0106cba65ceaf3aa95/maven4.log)
- [55931d4c3a2e17683deff5d4e8065a929866290b/maven4.log](55931d4c3a2e17683deff5d4e8065a929866290b/maven4.log)
- [d54b56b91c11f21b97d4903143b04b7c1f10c255/maven4.log](d54b56b91c11f21b97d4903143b04b7c1f10c255/maven4.log)
- [f78d34b82926216c0f203c0350f646d481c675e3/maven4.log](f78d34b82926216c0f203c0350f646d481c675e3/maven4.log)
- [b6c9b74915e6fb8a972e243f23af2631fce44eb4/maven4.log](b6c9b74915e6fb8a972e243f23af2631fce44eb4/maven4.log)
- [1266a8c84cd04cedfa316bed94b1e9b014da872f/maven4.log](1266a8c84cd04cedfa316bed94b1e9b014da872f/maven4.log)
- [dc9a40fde9a9fee5aaec3f60695385ba539406d4/maven4.log](dc9a40fde9a9fee5aaec3f60695385ba539406d4/maven4.log)
- [07fad972bb884e9fa6143b4f870d08305811607d/maven4.log](07fad972bb884e9fa6143b4f870d08305811607d/maven4.log)
- [ff77ec9ef379e9fee5f45f4fd88230e9bbbe4b89/maven4.log](ff77ec9ef379e9fee5f45f4fd88230e9bbbe4b89/maven4.log)
- [1ef97ea6c5b6e34151fe6167001b69e003449f95/maven4.log](1ef97ea6c5b6e34151fe6167001b69e003449f95/maven4.log)

## DEPENDENCY_LOCK_MISMATCH (13)

**Fix:** Re-generate the lock file under Maven 4: `mvn se.vandmo:dependency-lock-maven-plugin:update`. Maven 4's dependency resolution order differs slightly from Maven 3, so old lock files will mismatch.


- [c5ddd70e8ce1555bdfd337e563b08d87ec6dc826/maven4.log](c5ddd70e8ce1555bdfd337e563b08d87ec6dc826/maven4.log)
- [d6eda931038e0088d03c4bdf4b3006eb87c551e2/maven4.log](d6eda931038e0088d03c4bdf4b3006eb87c551e2/maven4.log)
- [8c4d6c00ba37fa1d09faa90798f0c33e97d0fb69/maven4.log](8c4d6c00ba37fa1d09faa90798f0c33e97d0fb69/maven4.log)
- [53f3dbca3fdadef153a5357a9804c1f823384309/maven4.log](53f3dbca3fdadef153a5357a9804c1f823384309/maven4.log)
- [abfb7dd92cff85ddb69f70666f3f1705bbf55c78/maven4.log](abfb7dd92cff85ddb69f70666f3f1705bbf55c78/maven4.log)
- [1a771d2096b043dc34982d78494b2f14f506176a/maven4.log](1a771d2096b043dc34982d78494b2f14f506176a/maven4.log)
- [548f2efd47b36a4200b92ffa2995aacdc2cf621a/maven4.log](548f2efd47b36a4200b92ffa2995aacdc2cf621a/maven4.log)
- [732226b0d9e52a355dc50143faeba6a84bb2aedc/maven4.log](732226b0d9e52a355dc50143faeba6a84bb2aedc/maven4.log)
- [836c7cc2fe1917d9ec96528d49a4150b20b540d2/maven4.log](836c7cc2fe1917d9ec96528d49a4150b20b540d2/maven4.log)
- [37cf0a49373dbd339531a471ec503f9b9e4a34cb/maven4.log](37cf0a49373dbd339531a471ec503f9b9e4a34cb/maven4.log)
- [72ff6be0c4702d86ad2435292f034948ec896cc2/maven4.log](72ff6be0c4702d86ad2435292f034948ec896cc2/maven4.log)
- [5d2262cc6f0c2e204919fd91338f2004891cb1be/maven4.log](5d2262cc6f0c2e204919fd91338f2004891cb1be/maven4.log)
- [bd9245e7626d7844d1b67dda4396ca523e9e3687/maven4.log](bd9245e7626d7844d1b67dda4396ca523e9e3687/maven4.log)

## DUPLICATE_DEPENDENCY (10)

**Fix:** Remove the duplicate `<dependency>` entry from `<dependencyManagement>` in `pom.xml`. Maven 4 rejects duplicate declarations (same groupId:artifactId:type:classifier) at parse time; Maven 3 silently picked the last one.


- [3f30dfff617fd652412260ecf648a25769a27101/maven4.log](3f30dfff617fd652412260ecf648a25769a27101/maven4.log)
- [4259baebb426fefbe9dbee26725d6803170dcb85/maven4.log](4259baebb426fefbe9dbee26725d6803170dcb85/maven4.log)
- [8198d6d20e710afae3e29af68160883b3b1203d2/maven4.log](8198d6d20e710afae3e29af68160883b3b1203d2/maven4.log)
- [874ed893a4e46ea5182be2be054715967e58f08f/maven4.log](874ed893a4e46ea5182be2be054715967e58f08f/maven4.log)
- [4bba3fb6147e72946f64724fe55eee5d15ff6206/maven4.log](4bba3fb6147e72946f64724fe55eee5d15ff6206/maven4.log)
- [e05121a99cab7dc2b7c88094c2aa2a43e453ee68/maven4.log](e05121a99cab7dc2b7c88094c2aa2a43e453ee68/maven4.log)
- [f61b113c1493d02611bc5cdc9a80e8af0abf2035/maven4.log](f61b113c1493d02611bc5cdc9a80e8af0abf2035/maven4.log)
- [e5801e7457483c9f9e92cd10779e1281fc930fd7/maven4.log](e5801e7457483c9f9e92cd10779e1281fc930fd7/maven4.log)
- [532ba2101316be180cc065ba70760fcb4a650056/maven4.log](532ba2101316be180cc065ba70760fcb4a650056/maven4.log)
- [72c6b8dd53be12cc675d6c49ca55b18c27e94f1a/maven4.log](72c6b8dd53be12cc675d6c49ca55b18c27e94f1a/maven4.log)

## PARENT_POM_CYCLE (9)

**Fix:** Break the circular parent reference. Usually caused by a BOM that imports itself or a multi-module project where a child is also declared as parent. Restructure into a dedicated parent POM module separate from the BOM.


- [746846c51abe7965b025db5f7cd5b4a16fa6e535/maven4.log](746846c51abe7965b025db5f7cd5b4a16fa6e535/maven4.log)
- [4d3c6dbc0bd9ca66b24af0bafda70c7355c3daf7/maven4.log](4d3c6dbc0bd9ca66b24af0bafda70c7355c3daf7/maven4.log)
- [4ef9928eee8a3b357b15fd1c5b195800add63802/maven4.log](4ef9928eee8a3b357b15fd1c5b195800add63802/maven4.log)
- [20e787c7e1e6d0f8de9449551a3749575348a341/maven4.log](20e787c7e1e6d0f8de9449551a3749575348a341/maven4.log)
- [5e61a76ca214c41d7e1c079e6a81c3ba9c26772e/maven4.log](5e61a76ca214c41d7e1c079e6a81c3ba9c26772e/maven4.log)
- [e181647f2d24f810e59c6b903b1d8cf6680ac5a3/maven4.log](e181647f2d24f810e59c6b903b1d8cf6680ac5a3/maven4.log)
- [9b63e53888ebdd9c84f4eec3cb661299dea41344/maven4.log](9b63e53888ebdd9c84f4eec3cb661299dea41344/maven4.log)
- [43020e184328b155c2474632b19c63d1b931c995/maven4.log](43020e184328b155c2474632b19c63d1b931c995/maven4.log)
- [863f64e40b3dce33fe357e8096138830254c15ea/maven4.log](863f64e40b3dce33fe357e8096138830254c15ea/maven4.log)

## SESSION_CONTAINER_NULL (7)

**Fix:** Upgrade the offending plugin (typically `exec-maven-plugin` to `3.2.0+`). `MavenSession.getContainer()` now returns `null` in Maven 4 — newer plugin versions use the injected `PlexusContainer` directly.


- [533591407112b14f7ff2d46d515c599eb4674e95/maven4.log](533591407112b14f7ff2d46d515c599eb4674e95/maven4.log)
- [c007e01d4bad22201d0ce3dcf3a4dc04bc282647/maven4.log](c007e01d4bad22201d0ce3dcf3a4dc04bc282647/maven4.log)
- [f5bc873a4b68e87761a65064ebea9ad8c3fb085f/maven4.log](f5bc873a4b68e87761a65064ebea9ad8c3fb085f/maven4.log)
- [bab3a3c5b1aa9ee57dcbff7f146576f2c0c7251d/maven4.log](bab3a3c5b1aa9ee57dcbff7f146576f2c0c7251d/maven4.log)
- [e35a97cf177d8cd7d022c4adca7da24f1a17a2eb/maven4.log](e35a97cf177d8cd7d022c4adca7da24f1a17a2eb/maven4.log)
- [89c60030429466a6cca77c2549a53a17c085033d/maven4.log](89c60030429466a6cca77c2549a53a17c085033d/maven4.log)
- [41164ec5392b2c853ae871f10a9541c055028b36/maven4.log](41164ec5392b2c853ae871f10a9541c055028b36/maven4.log)

## JGITVER_EXTENSION (6)

**Fix:** Move jgitver from the `<plugins>` section to a Maven core extension. Create/update `.mvn/extensions.xml` to declare jgitver there instead. Maven 4 requires version-computing extensions to run before POM parsing.


- [43b3a858b77ec27fc8946aba292001c3de465012/maven4.log](43b3a858b77ec27fc8946aba292001c3de465012/maven4.log)
- [0305beafdecb0b28f7c94264ed20cdc4e41ff067/maven4.log](0305beafdecb0b28f7c94264ed20cdc4e41ff067/maven4.log)
- [d401e189fb6435110e3dc4ca1a94838f167e7ddf/maven4.log](d401e189fb6435110e3dc4ca1a94838f167e7ddf/maven4.log)
- [b5eb721e10cb05e68c0283d8a76c46cc257a0331/maven4.log](b5eb721e10cb05e68c0283d8a76c46cc257a0331/maven4.log)
- [04c07b066b60a2e9d4f797b98dcd439a3a42f0b9/maven4.log](04c07b066b60a2e9d4f797b98dcd439a3a42f0b9/maven4.log)
- [18e92e4eb94da9f20b9864486865295176ebd83a/maven4.log](18e92e4eb94da9f20b9864486865295176ebd83a/maven4.log)

## OTHER (5)

**Fix:** These are one-off failures requiring individual investigation:
- `2dfaa41bfb97`: Velocity template crash in `maven-changelog-plugin` — upgrade the plugin
- `83a25390887d`: `maven-source-plugin:4.0.0-beta-1` regression — pin to `3.3.1` (stable)
- `b1a941400d68`: `maven-resources-plugin:4.0.0-beta-1` regression — pin to `3.3.1` (stable)
- `0771fe8d060a`: `maven-jar-plugin:3.0.0` is too old for Maven 4 — upgrade to `3.4.0+`
- `fb40f5e5fc20`: `quarkus-bootstrap-maven-plugin:1.13.7.Final` incompatible with Maven 4 — upgrade Quarkus to 3.x+


- [2dfaa41bfb97674d11f09a5885011f19808548a3/maven4.log](2dfaa41bfb97674d11f09a5885011f19808548a3/maven4.log)
- [83a25390887d8afa7d43b70070d710da3c90a7a6/maven4.log](83a25390887d8afa7d43b70070d710da3c90a7a6/maven4.log)
- [b1a941400d68445d76056ab8833cd6d2e3455954/maven4.log](b1a941400d68445d76056ab8833cd6d2e3455954/maven4.log)
- [0771fe8d060aec9dc22344b8946e25ba8500afb9/maven4.log](0771fe8d060aec9dc22344b8946e25ba8500afb9/maven4.log)
- [fb40f5e5fc20ca31cf9fdadd5ace53f7de5ea093/maven4.log](fb40f5e5fc20ca31cf9fdadd5ace53f7de5ea093/maven4.log)

## TAKARI_LIFECYCLE (4)

**Fix:** Replace `takari-lifecycle-plugin` with standard Maven plugins — `maven-compiler-plugin` for compilation, `maven-jar-plugin` for packaging. Takari is abandoned (last release 2021) and calls a Maven 4 API that now throws `UnsupportedOperationException`.


- [249c3b394540fde4fcb72f66172af5e02b9c637e/maven4.log](249c3b394540fde4fcb72f66172af5e02b9c637e/maven4.log)
- [d9a0df6feee60bb6d7d301f251cab386168eecd1/maven4.log](d9a0df6feee60bb6d7d301f251cab386168eecd1/maven4.log)
- [1e17e176460ab4283e463e62fece844d341da7f0/maven4.log](1e17e176460ab4283e463e62fece844d341da7f0/maven4.log)
- [a9df7b2235224fcabefa1d62e8956911aa5bb825/maven4.log](a9df7b2235224fcabefa1d62e8956911aa5bb825/maven4.log)

