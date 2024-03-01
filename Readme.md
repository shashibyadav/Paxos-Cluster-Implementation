### Author :- Shashi Bhushan Yadav

This zip contains all the files for the project including Executable jars in folder named "Executables". There are two executables Server.jar and Client.jar for the project.

### **How to run Server Executable** :-

Before running the server put a server configuration file named config.json in the same directory.

File example is given below:- 
	
	{
		"electLeader": true,
		"acceptRandomErrorProbability": 0.0,
		"requestAttempts": 5,
		"delay": 1000,
		"allowNodeRestart": true,
		"nodes": {
			"addressList": [
					{
						"host": "localhost",
						"port": 40001,
						"serviceNameList": ["StoreService"]
					},
					{
						"host": "localhost",
						"port": 40002,
						"serviceNameList": ["StoreService"]
					},
					{
						"host": "localhost",
						"port": 40003,
						"serviceNameList": ["StoreService"]
					},
					{
						"host": "localhost",
						"port": 40004,
						"serviceNameList": ["StoreService"]
					},
					{
						"host": "localhost",
						"port": 40005,
						"serviceNameList": ["StoreService"]
					}
					]
			}
	}

In this configuration file, Key `"electLeader"` when true will make application to elect leader and perform all operations through leader.

Key `"acceptRandomErrorProbability"` will throw random errors in accept phase with the given probability.

Key `"requestAttempts"` is number of times each request will try and wait for a leader to be elected in a leader based application before throw transaction failure exception.

Key `"delay"` is milliseconds interval that each node take to perform cleanup or any wait in thread.

Key `"allowNodeRestart"` when true enables another thread that runs with `3 * delay` interval and will try to restart any failed node.

Key `"addressList"` contains an array of `"host"`, `"port"` and `"serviceNameList"` objects for individual node configurations.

Key `"serviceNameList"` an array that contains the interface names exposed to user.

Command to start Server:-

`-> java -jar Server.jar`

Once started Server will create another directory "logs" that will contain logs of individual servers by following naming convention:- 

`Server_{server port}_{timestamp}.txt`

Also, whenever client issues a request to log state all participants will log the snapshot of store in their corresponding log files.


### **How to run Client Executable** :- 

The Client executable can be start by following command:-

`-> java -jar Client.jar RPC <server-host> <RPC-server-port> <RPC-Service-Key>`

Here, Since we are building upon the code of project 2, we will be using same servers except they will be participants in this case.

And, Client can connect to any one of the participants to run. 

Ex. `java -jar Client.jar RPC localhost 40002 StoreService`

This command will connect to participant running on `localhost:40002`

--------------------------------------------------------------------
Entry point Java files are located in src/main/java/com/project/four

How to run Server using PaxosServerDebug.java :-
	->	`javac PaxosServerDebug.java`

Then,

	->	`java PaxosServerDebug`

Similarly, this Class will require config.json just as in Server.jar executable mentioned above.


How to run Client using ClientDebug.java :- 
	->	`javac ClientDebug.java`

Then,

		-> java ClientDebug RPC <server-host> <RPC-server-port> <RPC-Service-Key>

Same as in executables:-

Ex. `java ClientDebug RPC localhost 40002 StoreService`

--------------------------------------------------------------------

Screenshots of the terminal Output are present in folder named Screenshots

--------------------------------------------------------------------

# **Summary of working** 

After starting the server first using any of the start command start the client corresponding to the protocol. 
Client is written to test dummy data when it starts it will put 5 key-value pairs then fetch them. 
Then it will initiate state logging. After printing those five fetched values client will ask for deletion of these five keys and fetch them from server which will result in values shown as null as client didn't receive any data for corresponding keys from Server.
After this client becomes interactive and user can perform PUT, GET, DELETE operations.
User can also exit the client or can ask server and client both to exit or can initiate a state save depending upon the selection.
Also, Server creates a log file starting with the name "Server_{server-port}_*" that can be used to assess server health or status as well as to check logged states of the servers.
Each client can only connect to one node and GET as well as PUT and DELETE under transaction is responsibility of this node to execute utilizing leader or distributed paxos depending upon the configuration.

### **Leader Election**

When started with `"electLeader": true`, application starts with `"serverId = 0"` as leader. Then any server can be closed by providing the `serverId` to client, where `serverId` is the index of array in configuration file.
**DO NOT CLOSE the node that client is connected to as there is not load balancer between cluster and client and client connects to a single node by port.** This will close the connection and client have to reconnect to a different node if this happened.

Once leader dies, every node compete to be the leader in interval given as `"delay"` key in config. During this election process all requests are stopped for the amount of `"requestAttempts"` provided in config. If leader is elected before provided number of attempts, request proceeds as planned otherwise `transaction failure` exception is thrown to user.

Also, if `"allowNodeRestart": true` new server will be started in place of failed node. We can connect to any other node than leader **(which is `"serverId = 0` in the start, and can be retrieved by requesting globalstates and checking the logs)** and call stop on leader to start an election process and if restart is enabled another node will be started in place of the stopped leader.

### **When Leader Election is not enabled**

All the requests follow paxos algorithm and compete for each value updates, this can result in some missed updates as some other value is agreed upon during this process.
However, final states will always be consistent across all servers. This, will not happen in this scale in real-world as not all users will be competing for same value and in case if they do whosoever will win through consensus will win and it's value will be retained.

For optimal use case run this application will leader enabled as this will reduce the amount of resource usage across nodes.

To generate random errors in accept phase we can set the `"acceptRandomErrorProbability"` with the probability we want to throw exception in accept phase. Although, when leader is enabled, this will only happen while leader election as paxos is only used in election.

---------------------------------------------------------------------

# **Executive Summary** 

### _Assignment Overview_:-

As an increment over Project 3, this project clarifies how Paxos Algorithm work in the background. 
I have used Java RMI for RPC communication implemented over TCP/IP communication utilizing serialization, networking, and class facilities already available in Java. 
It helped me in understanding how to implement data replication utilizing Paxos algorithm. Furthermore, I learned using paxos algorithm for leader election. Providing me initial insights in cluster management and implementing leader elections and orchestrating replication on multiple servers.
It provides further understanding of synchronization between servers in a cluster as well as inter-thread communication in java.
It also handles all the concurrent and multithreading operations on the server, utilizing multi-threading and thread pools along with Paxos algorithm using RMI. 
The purpose of this project is to have a decent understanding of working of data replication, implementing transactions/locks and how concurrency on clusters as well as How Debugging/ Exceptional Handling works in a distributed systems.


### _Technical Impression_:-

Completing this project has been a thought-provoking experience. 
A more structured method of communication was made possible by implementing RPC using Java RMI, which also enabled smooth communication between the client and server as well as within the cluster. 
RPC brought a new degree of efficiency and organization to communication that had previously relied on sockets. Utilizing Java RMI, I implemented two-phase protocol using a cluster of servers(numbers can be changed simply by adding more participants in config file) and learned a lot about synchronization and inter-thread communication using `Object.wait()` and `Object.notify()` methods provided in java. I also used `ExecutorService` and `synchronized` keyword for maintaining thread pools and synchronization, these helped me in further implementing cleanup and restart processes. 

Concurrent client requests were handled by thread pools provided by Java RMI, and data consistency required careful synchronization. 
A primary goal in preventing data conflicts was maintaining data consistency across multiple server as well as seamless leader election using Paxos algorithm if implemented.

This project has improved my knowledge of transactions and inter-thread(or process) communications while highlighting the value of concurrent programming in practical situations. 

Regarding enhancements, offering more detailed instructions on selecting an RPC framework and multi-threading techniques would be beneficial. This project can be improved by introducing more safeguards.
Overall, this project has expanded my knowledge of database programming and data integrity management on distributed systems.

By working of this project I understood about the workings of Paxos algorithm and how to build a server cluster in multi-threaded environment. 
My implementation of Cluster will start the number of defined nodes in config file and will elect a leader in case of leader node failure. 

I understood how client and server communicate and implement replication in a cluster and what kind errors or issues can arise in multiple clients (Ex. two different clients accessing common store cluster concurrently) thereby increasing reliablity and throughput of the entire system. 
There is a lot of improvement that can be performed in my implementation but I guess for now it satisfies the requirements of this project. 
