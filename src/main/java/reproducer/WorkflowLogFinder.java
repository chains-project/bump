package reproducer;

import com.fasterxml.jackson.databind.type.MapType;
import miner.BreakingUpdate;
import miner.GitHubAPITokenQueue;
import miner.JsonUtils;
import okhttp3.OkHttpClient;
import org.kohsuke.github.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The WorkflowLogFinder involves in downloading workflow log files related to the failed jobs in the updated dependency.
 */
public class WorkflowLogFinder {
    private final OkHttpClient httpConnector;
    private final GitHubAPITokenQueue tokenQueue;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * @param tokenQueue    a GitHubAPITokenQueue of GitHub API tokens.
     * @param httpConnector an OkHttpClient instance.
     */
    public WorkflowLogFinder(GitHubAPITokenQueue tokenQueue, OkHttpClient httpConnector) {
        this.httpConnector = httpConnector;
        this.tokenQueue = tokenQueue;
    }

    /**
     * Fetch the failed jobs in configured GitHub workflows for the breaking update pull request and extract the path
     * to the workflow log files.
     */
    public void extractWorkflowLogFile(String baseDownloadDirectory, String chromeDriverPath, String usrDataDirectory,
                                       BreakingUpdate bu) throws IOException {
        MapType jsonType = JsonUtils.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
        Path workflowLogFilePath = Path.of(baseDownloadDirectory + "/workflowLogLocations" + JsonUtils.JSON_FILE_ENDING);
        if (Files.notExists(workflowLogFilePath)) {
            Files.createFile(workflowLogFilePath);
        }
        Map<String, Map<String, Boolean>> workflowLogs = JsonUtils.readFromNullableFile(workflowLogFilePath, jsonType);
        if (workflowLogs == null) {
            workflowLogs = new HashMap<>();
        }

        String downloadDirectory = baseDownloadDirectory + "\\" + bu.breakingCommit + "\\";
        if (!Files.exists(Path.of(downloadDirectory))) {
            new File(downloadDirectory).mkdirs();

            String prUrl = bu.url;
            String[] urlParts = prUrl.split("/");
            String repoOwner = urlParts[3];
            String repoName = urlParts[4];
            int prNumber = Integer.parseInt(urlParts[6]);

            GitHub github = tokenQueue.getGitHub(httpConnector);
            GHRepository repository = github.getRepository(repoOwner + "/" + repoName);
            GHPullRequest pr = repository.getPullRequest(prNumber);
            GHWorkflowRunQueryBuilder query = pr.getRepository().queryWorkflowRuns()
                    .branch(pr.getHead().getRef())
                    .event(GHEvent.PULL_REQUEST)
                    .status(GHWorkflowRun.Status.COMPLETED)
                    .conclusion(GHWorkflowRun.Conclusion.FAILURE);
            List<GHWorkflowRun> workflowRuns = query.list().toList().stream()
                    .filter(run -> run.getHeadSha().equals(pr.getHead().getSha())).toList();
            Map<String, Boolean> logLocation = new HashMap<>();

            for (GHWorkflowRun workflowRun : workflowRuns) {
                for (GHWorkflowJob ghWorkflowJob : (workflowRun.listAllJobs().toList()
                        .stream().filter(run -> run.getConclusion().equals(GHWorkflowRun.Conclusion.FAILURE))
                        .toList())) {
                    String jobUrl = String.valueOf(ghWorkflowJob.getHtmlUrl());
                    if (downloadLogFile(jobUrl, downloadDirectory, chromeDriverPath, usrDataDirectory))
                        logLocation.put(jobUrl, true);
                    else
                        logLocation.put(jobUrl, false);
                }
            }
            workflowLogs.put(bu.breakingCommit, logLocation);

            JsonUtils.writeToFile(workflowLogFilePath, workflowLogs);
        }
    }

    /**
     * Download the workflow log files using a Selenium web crawler.
     */
    private boolean downloadLogFile(String prUrl, String downloadDirectory, String chromeDriverPath, String usrDataDirectory)
            throws IOException {
        if (Files.list(Path.of(downloadDirectory)).findAny().isPresent())
            return true;

        if (System.getProperty("os.name").startsWith("Windows"))
            System.setProperty("webdriver.chrome.driver", chromeDriverPath);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");

        if (usrDataDirectory != null)
            options.addArguments("user-data-dir=%s".formatted(usrDataDirectory));
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDirectory);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(prUrl);
            Thread.sleep(7500);
            WebElement settingsButton = driver.findElement(By.cssSelector(".btn-link .octicon-gear path"));
            Actions actions = new Actions(driver);
            actions.moveToElement(settingsButton).click().build().perform();

            Thread.sleep(2500);
            WebElement downloadOption = driver.findElement(By.xpath("//*[@id='logs']/div[1]/div/details/details-menu/a[1]"));
            downloadOption.click();
            Thread.sleep(10000);
            log.info("Successfully downloaded the log files");
            return true;
        } catch (NoSuchElementException e) {
            log.error("Logs are expired and no longer can be downloaded");
            return false;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            driver.quit();
        }
    }
}