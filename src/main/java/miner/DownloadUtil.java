package miner;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class DownloadUtil {
    private final BreakingUpdate breakingUpdate;
    String baseURL = "https://mvnrepository.com/artifact";

    public DownloadUtil(BreakingUpdate breakingUpdate) {
        this.breakingUpdate = breakingUpdate;
    }


    /**
     * create jar file
     */
    public void downloadJarFile() {
        URL url;
        try {
            url = new URL(createURL(breakingUpdate));
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            System.out.println("url");
            FileOutputStream fos = new FileOutputStream("./reproduction/jars/"+breakingUpdate.dependencyArtifactID+"-"+breakingUpdate.newVersion+".jar");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String createURL(BreakingUpdate breakingUpdate) {

        String artifactID = "/" + breakingUpdate.dependencyArtifactID;
        String groupID = "/" + breakingUpdate.dependencyGroupID;
        String version = "/" + breakingUpdate.newVersion;
        String name= "/"+artifactID+"-"+version;

        return baseURL + artifactID + groupID + version+name+".jar";
//        return "https://repo1.maven.org/maven2/dev/gradleplugins/gradle-test-kit/7.6/gradle-test-kit-7.6.jar";

    }

}
