# Cluster Healing with Zookeeper

## Introduction
Implemented an application which allows Zookeeper to monitor and automatically heal a cluster of
 workers. The cluster healer will launch the requested number of workers, then monitor the cluster to ensure that the
  requested number of workers is running. If a worker dies, the cluster healer launches more workers to keep the
   total number of workers at the requested number.

## Operation
On startup, the cluster healer
- connects to Zookeeper
- checks if the `/workers` parent znode exists, and if it doesn't, it creates it.
- starts the requested number of workers
    - the number of workers and the path to the worker application are passed in as command-line arguments.
- watch the cluster to check if workers die
    - replacement workers are started when workers die. 
    - the number of running workers are always equal to the requested number.
  
## Workers  
For this assignment I was provided with a worker application, `faulty-worker.jar`. On startup, this application connects to Zookeeper
 and creates a sequential znode called `worker_` under the `/workers` parent znode, e.g. `/workers
 /worker_0000001`. This worker is programmed to continually crash at random intervals, which will cause the znode it
  created to be removed.

### `ClusterHealer.java`
This class is responsible for initialising and monitoring the health of the cluster.

- `connectToZookeeper`
    - instantiate a Zookeeper client, creating a connection to the Zookeeper server.
- `process(WatchedEvent event)`
    - Handle Zookeeper events related to: 
        - Connecting to the Zookeeper server
            - Prints out this message to System.out: `Successfully connected to Zookeeper`
        - Disconnecting from the Zookeeper server
            - Prints out this message to System.out: `Disconnected from Zookeeper`        
        - Changes in the number of workers currently running.
- `initialiseCluster()`
    - Checks if the `/workers` parent znode exists, and creates it if it doesn't. (persistent)
     - Checks if workers need to be launched.
- `checkRunningWorkers()`
    - Checks how many workers are currently running. If less than the required number, then program starts a new worker.
- `run()`
    - Keeps the application running waiting for Zookeeper events.
-  `close()` 
    - Closes the Zookeeper client connection.
        
- `startWorker()`
    - Starts a new worker using the path provided as a command line argument.
    
### `Application.java`
Contains a main method which creates a new `ClusterHealer` instance and calls methods on it to:
- connect to Zookeeper
- initialise the cluster
- keep running waiting for Zookeeper events
                                   

## Building and Running the Cluster Healer Application
### Building
Use the maven `package` goal from the IntelliJ tool window to build an executable jar from your code. The jar will be called `cluster-healer-1.0-SNAPSHOT-jar-with-dependencies.jar`and it will be in the `target` folder in your project. 

### Running
Running this command from the `cluster-healer` project directory will start up 3 worker instances using the provided `faulty-worker.jar`, and will monitor the cluster to ensure that 3 instances are always running. **Ensure that you've started the Zookeeper server first**.
```
java -jar target/cluster-healer-1.0-SNAPSHOT-jar-with-dependencies.jar 3 ../faulty-worker.jar
