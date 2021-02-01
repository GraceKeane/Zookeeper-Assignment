import org.apache.zookeeper.KeeperException;

import java.io.IOException;

/*
************************************************************************************************************************
ATTENTION GMIT STUDENTS!!!!!

PLEASE DON'T MAKE CHANGES TO THIS FILE UNLESS ADVISED BY YOUR LECTURER.
YOU'RE LIKELY TO BREAK THE AUTOGRADING ENVIRONMENT OR MAKE SOME SORT OF OTHER MESS, RESULTING IN PAIN, TEARS, AND
OTHER UNPLEASANTNESS.
************************************************************************************************************************
*/

public class Application {
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        if (args.length != 2) {
            System.out.println("Expecting parameters <number of workers> <path to worker jar file>");
            System.exit(1);
        }

        int numberOfWorkers = Integer.parseInt(args[0]);
        String pathToWorkerProgram = args[1];
        ClusterHealer clusterHealer = new ClusterHealer(numberOfWorkers, pathToWorkerProgram);
        clusterHealer.connectToZookeeper();
        clusterHealer.initialiseCluster();
        clusterHealer.run();
        clusterHealer.close();
    }
}
