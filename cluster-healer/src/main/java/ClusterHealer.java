import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Distributed Systems assessment 1.
 * Zookeeper cluster-healer program.
 *
 * @author Grace Keane
 * @version Java15
 */

public class ClusterHealer implements Watcher {

    // Path to the worker jar
    private final String pathToProgram;
    // The number of worker instances we need to maintain at all times
    private final int numberOfWorkers;
    // URL of the Zookeeper server (hostname & default port)
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    // Variable to check how frequently it is connected to Zookeeper server (3s)
    private static final int SESSION_TIMEOUT = 3000;
    // Stores the name of the parent
    public static final String PARENT_ZNODE = "/workers";
    // Main connection to Zookeeper server
    private ZooKeeper zooKeeper;

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        // Creating a new instance of class & taking in numOfWorkers & path
        ClusterHealer clusterHealer = new ClusterHealer(3, "./target/cluster-healer-1.0-SNAPSHOT-jar-with-dependencies.jar");
        // Calling methods
        clusterHealer.connectToZookeeper();
        clusterHealer.initialiseCluster();
        clusterHealer.checkRunningWorkers();
        clusterHealer.run();
        clusterHealer.close();
    }

    /**
     * Initializing my cluster healer.
     *
     * @param numberOfWorkers Takes in number of requested workers.
     * @param pathToProgram Takes in path to the cluster-healer jar file.
     */
    public ClusterHealer(int numberOfWorkers, String pathToProgram) {
        this.numberOfWorkers = numberOfWorkers;
        this.pathToProgram = pathToProgram;
    }

    /**
     * Checking if the `/workers` parent z-node exists, and creates it if it doesn't. Then checks if workers
     * need to be launched.
     * Parent z-node type - PERSISTENT.
     * PERSISTENT - Persistent z-node is alive even after the client which created that z-node is disconnected.
     *
     * @throws KeeperException
     * @throws InterruptedException
     * @throws IOException
     */
    public void initialiseCluster() throws KeeperException, InterruptedException, IOException {
        // Watching and storing the parent z-node in a stat variable
        Stat x = zooKeeper.exists(PARENT_ZNODE, true);
            if (x == null){
                System.out.println("PARENT NODE IS NULL");
                // Creating the parent z-node and initializing it to be persistent
                zooKeeper.create(PARENT_ZNODE, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                System.out.println("PARENT NODE IS NOT NULL");
            }
        // Calling method if parent is found
        checkRunningWorkers();
    }

    /**
     * Instantiates a Zookeeper client, creating a connection to the Zookeeper server.
     *
     * @throws IOException
     */
    public void connectToZookeeper() throws IOException {
        // Creating a new ZooKeeper object and saving in Zookeeper variable
        // Creates new connection to Zookeeper
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    /**
     * Keeps the application running waiting for Zookeeper events.
     *
     * @throws InterruptedException
     */
    public void run() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    /**
     * Closes the Zookeeper client connection.
     *
     * @throws InterruptedException
     */
    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    /**
     * Handles Zookeeper events related to: - Connecting and disconnecting from the Zookeeper server. - Changes in the
     * number of workers currently running.
     *
     * @param watchedEvent Watches events regarding connected, disconnected, node deleted & node children change
     *
     */
    @Override
    public void process(WatchedEvent watchedEvent) {
        switch (watchedEvent.getType()) {
            case None: // Case for disconnect and connect
                // Watching connected event
                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("Successfully connected to ZooKeeper");
                } else {
                    // Watching disconnected event
                    synchronized (zooKeeper) {
                        System.out.println("Disconnected from Zookeeper");
                        zooKeeper.notifyAll();
                    }
                }
                break;
            case NodeDeleted: // If node that is being watched dies then it calls checkRunningWorkers()
                try {
                    checkRunningWorkers();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (KeeperException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case NodeChildrenChanged: // If children node that's being watched changes then it calls checkRunninWorkers()
                try {
                    checkRunningWorkers();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (KeeperException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
    }

    /**
     * Checks how many workers are currently running.
     * If less than the required number, then starts a new worker.
     *
     * @throws InterruptedException
     * @throws KeeperException
     * @throws IOException
     */
    public void checkRunningWorkers() throws IOException, KeeperException, InterruptedException {
        // Getting a list of the children nodes
        List<String> childrenList = zooKeeper.getChildren(PARENT_ZNODE, this);
        // Getting the amount of children
        childrenList.size();

        if (childrenList.size() < numberOfWorkers) {
            System.out.println("There are currently " + childrenList.size() + " Workers");
            System.out.println("There are currently " + childrenList + " Workers");
            System.out.println("Child list is less than numOfWorkers - calling startWorker()");
            // Calling startWorker() if the number of children is less than the required workers
            startWorker();
            }
        }

    /**
     * Starts a new worker using the path provided as a command line parameter.
     *
     * @throws IOException
     */
    public void startWorker() throws IOException {
        File file = new File(pathToProgram);
        String command = "java -jar " + file.getName();
        System.out.println(String.format("Launching worker instance : %s ", command));
        Runtime.getRuntime().exec(command, null, file.getParentFile());
    }
}
