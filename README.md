![Laminar Logo](logo.webp)
### Laminar Server Installation and Execution
Open a new terminal with the new `Laminar` directory.
```
/data/Laminar> 
```
Clone the server application repository.
```
/data/Laminar> git clone https://github.com/StreamingFlow/dispel4py-server.git
```
Enter the server directory.
```
/data/Laminar> cd dispel4py-server
```
To manage permission issues, run:
```
/data/Laminar/dispel4py-server> chmod +x gradlew
```
To configure the required fields, navigate to `/src/main/resources/application.properties` and fill in the required variables

To run the server
```
/data/Laminar/dispel4py-server> ./gradlew bootRun
```
The server will now be running, and ready to receive requests. You should see this sample output
```
/data/Laminar/dispel4py-server>
...
com.dispel4py.rest.RestApplication: Started RestApplication in 66.905 seconds (JVM running for 69.911)
...
```

### Docker Execution 

To run the application in a docker container follow these intstructions 

Clone the server application repository.

```
/data/Laminar> git clone https://github.com/Laminar-2/dispel4py-server.git
```
Enter the server directory.
```
/data/Laminar> cd dispel4py-server
```
Run docker compose to load up the server and database. The first time we recommend to use --build flag. 
```
docker compose up --build
```

Next time you could use:
```
docker compose up
```

If you need to rebuild the Docker containers (for instance, after making configuration changes), you can do so by following these steps:

First, bring down the running containers:
```
docker-compose down
```
Then, rebuild and start the containers:
```
docker-compose up --build
```
By following these steps, you can ensure that the server is properly configured and running efficiently.

You can also prune the data from the docker, if you need to:
```
 docker system prune -a
```
We recommend to do this step after `docker-compose down` and before `docker-compose up --build`. But be carreful, this will delete the full registry database information.

## Other Laminar components

The [laminar client](https://github.com/StreamingFlow/dispel4py-client) offers a user-friendly interface for registering and managing Processing Elements (PEs) as well as stream-based dispel4py workflows. For detailed guidance on how to interact with the system, please refer to the [user manual](https://github.com/StreamingFlow/dispel4py-client/wiki) available on the project's wiki.

In addition, the [laminar execution-engine](https://github.com/StreamingFlow/dispel4py-execution) is a critical component that must be installed, either locally or remotely, to facilitate the serverless execution of workflows.

