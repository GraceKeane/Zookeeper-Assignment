import com.github.blindpirate.extensions.CaptureSystemOutput;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClusterHealerTest {

    private static final String WORKER_ZNODE = "/worker_";
    private static final String PATH_TO_PROGRAM = "./";
    private static TestingServer zkServer;
    private static ZooKeeper zooKeeper;
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String WORKERS_PARENT_ZNODE = "/workers";


    @BeforeAll
    public static void setUp() throws Exception {
        zkServer = new TestingServer(2181, true);
        zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, watchedEvent -> {
        });
    }

    @AfterEach
    private void tearDown() throws KeeperException, InterruptedException {
        if (zooKeeper.exists(WORKERS_PARENT_ZNODE, false) != null) {
            ZKUtil.deleteRecursive(zooKeeper, WORKERS_PARENT_ZNODE);
        }
    }

    @AfterAll
    public static void shutDown() throws Exception {
        zkServer.stop();
    }

    // Check if ClusterHealer connects to zookeeper and prints out a connected message
    @Test
    @CaptureSystemOutput
    public void processConnected(CaptureSystemOutput.OutputCapture outputCapture) throws IOException, InterruptedException {
        ClusterHealer healer = new ClusterHealer(0, PATH_TO_PROGRAM);
        outputCapture.expect(containsStringIgnoringCase("connected"));
        healer.connectToZookeeper();
        healer.close();
    }

    // Check if ClusterHealer disconnects from zookeeper and prints out a disconnected message
    @Test
    @CaptureSystemOutput
    public void processDisconnected(CaptureSystemOutput.OutputCapture outputCapture) throws IOException, InterruptedException {
        ClusterHealer healer = new ClusterHealer(0, PATH_TO_PROGRAM);
        healer.connectToZookeeper();
        outputCapture.expect(containsStringIgnoringCase("disconnected"));
        healer.close();
    }

    // Creates the /workers parent znode
    @Test
    void initialiseCreateWorkersZnode() throws KeeperException, InterruptedException, IOException {
        ClusterHealer healer = new ClusterHealer(0, PATH_TO_PROGRAM);
        healer.connectToZookeeper();
        healer.initialiseCluster();
        Stat result = zooKeeper.exists(WORKERS_PARENT_ZNODE, false);

        assertNotNull(result, "/workers parent znode wasn't created.");
        healer.close();
    }

    // Check if /workers znode is of the correct type
    @Test
    void initialiseCreateWorkersZnodeMode() throws KeeperException, InterruptedException, IOException {
        ClusterHealer healer = new ClusterHealer(0, PATH_TO_PROGRAM);
        healer.connectToZookeeper();
        healer.initialiseCluster();
        Stat result = zooKeeper.exists(WORKERS_PARENT_ZNODE, false);
        assertEquals(0, result.getEphemeralOwner(), "/workers parent znode is of the wrong type");
        healer.close();
    }

    // Doesn't create /workers parent znode if it already exists
    @Test
    void initialiseCreateWorkersZnodeAlreadyExists() throws KeeperException, InterruptedException, IOException {
        ClusterHealer healer = new ClusterHealer(0, PATH_TO_PROGRAM);
        healer.connectToZookeeper();
        healer.initialiseCluster();
        Stat result = zooKeeper.exists(WORKERS_PARENT_ZNODE, false);
        Assertions.assertAll(
                () -> assertNotNull(result, "/workers parent znode wasn't created."),
                () -> assertDoesNotThrow(healer::initialiseCluster, "Shouldn't try to create /workers parent znode if it already exists")
        );
        healer.close();
    }

    @Test
    void initialiseChecksWorkers() throws IOException, InterruptedException, KeeperException {
        ClusterHealer spyHealer = Mockito.spy(new ClusterHealer(0, PATH_TO_PROGRAM));
        doNothing().when(spyHealer).checkRunningWorkers();

        spyHealer.connectToZookeeper();
        spyHealer.initialiseCluster();

        verify(spyHealer, description("The initialise method should call checkRunningWorkers()"))
                .checkRunningWorkers();
    }

    // Launch workers when event detected and more workers are required
    @Test
    void launchWorkersWhenNeeded() throws KeeperException, InterruptedException, IOException {
        ClusterHealer spyHealer = Mockito.spy(new ClusterHealer(3, PATH_TO_PROGRAM));
        doNothing().when(spyHealer).startWorker();

        helperCreateWorkerParentZnode();
        helperCreateWorkerChildZnodes(2);

        spyHealer.connectToZookeeper();

        spyHealer.process(new WatchedEvent(Watcher.Event.EventType.NodeChildrenChanged,
                Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE));

        verify(spyHealer, description("Should call checkRunningWorkers when workers start or crash"))
                .checkRunningWorkers();
        verify(spyHealer, description("Should start new worker when workers start or crash and " +
                "there aren't enough workers"))
                .startWorker();

        spyHealer.close();
    }

    // Don't launch workers when event detected and more workers are not required
    @Test
    void dontLaunchWorkersWhenNotNeeded() throws KeeperException, InterruptedException, IOException {
        ClusterHealer spyHealer = Mockito.spy(new ClusterHealer(2, PATH_TO_PROGRAM));
        doNothing().when(spyHealer).startWorker();

        helperCreateWorkerParentZnode();
        helperCreateWorkerChildZnodes(2);

        spyHealer.connectToZookeeper();

        spyHealer.process(new WatchedEvent(Watcher.Event.EventType.NodeChildrenChanged,
                Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE));

        verify(spyHealer, description("Should call checkRunningWorkers when workers start or crash"))
                .checkRunningWorkers();
        verify(spyHealer, never())
                .startWorker();

        spyHealer.close();
    }

    // Make sure launchWorkersIfNecessary() is called if event is correct
    @Test
    void processCorrectEvent() throws IOException, KeeperException, InterruptedException {
        ClusterHealer spyHealer = Mockito.spy(new ClusterHealer(3, PATH_TO_PROGRAM));
        ClusterHealer mockHealer = mock(ClusterHealer.class);
        doCallRealMethod().when(mockHealer).process(any(WatchedEvent.class));
        mockHealer.process(new WatchedEvent(Watcher.Event.EventType.NodeChildrenChanged,
                Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE));

        verify(mockHealer).checkRunningWorkers();
    }

    void helperCreateWorkerParentZnode() throws KeeperException, InterruptedException {
        helperCreateZnode(WORKERS_PARENT_ZNODE, CreateMode.PERSISTENT);
    }

    void helperCreateWorkerChildZnodes(int numberOfWorkerChildren) throws KeeperException, InterruptedException {
        for (int i = 0; i < numberOfWorkerChildren; i++) {
            helperCreateZnode(WORKERS_PARENT_ZNODE + WORKER_ZNODE, CreateMode.EPHEMERAL_SEQUENTIAL);
        }
    }

    void helperCreateZnode(String path, CreateMode mode) throws KeeperException, InterruptedException {
        zooKeeper.create(path, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, mode);
    }

    List<WatchedEvent> getEvents() {
        return Arrays.asList(
                new WatchedEvent(Watcher.Event.EventType.ChildWatchRemoved, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE),
                new WatchedEvent(Watcher.Event.EventType.PersistentWatchRemoved, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE),
                new WatchedEvent(Watcher.Event.EventType.NodeCreated, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE),
                new WatchedEvent(Watcher.Event.EventType.NodeDataChanged, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE),
                new WatchedEvent(Watcher.Event.EventType.NodeDeleted, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE),
                new WatchedEvent(Watcher.Event.EventType.DataWatchRemoved, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE),
                new WatchedEvent(Watcher.Event.EventType.None, Watcher.Event.KeeperState.SyncConnected, WORKERS_PARENT_ZNODE)
        );
    }
}