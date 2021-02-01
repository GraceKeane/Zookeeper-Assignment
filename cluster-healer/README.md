# To build the ClusterHealer
Run `mvn clean install`

## To run the clusterHealer, which in turn would launch and maintain 10 workers
Run `java -jar target/clusterHealer-1.0-SNAPSHOT-jar-with-dependencies.jar <number of workers> <path to woker jar>
Example: `java -jar target/clusterHealer-1.0-SNAPSHOT-jar-with-dependencies.jar 10 "../flakyworker/target/flaky.worker-1.0-SNAPSHOT-jar-with-dependencies.jar"`
