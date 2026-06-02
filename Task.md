# Basic Kubernetes

💡 [Tap here](https://new.oprosso.net/p/4cb31ec3f47a4596bc758ea1861fb624) **to leave your feedback on the project**. It's anonymous and will help our team make your educational experience better. We recommend completing the survey immediately after the project.

## Contents

1. [Chapter I](#chapter-i) 
2. [Chapter II](#chapter-ii) \
   2.1. [Ready-made manifest](#part-1-ready-made-manifest) \
   2.2. [Your own manifest](#part-2-your-own-manifest)

## Instructions

How to learn at “School 21”:

- Here, you’ll find a unique learning experience with a lot of freedom. You’re given a task and left to find your own way to solve it, using whatever resources work best for you — whether that’s the Internet or AI tools like GigaChat. Just be mindful of information quality: verify, think critically, analyze, and compare.
- Peer-to-peer (P2P) learning is the exchange of knowledge and experience with peers, where everyone acts as both mentor and student. This approach allows you to gain a deeper understanding of the material by learning from one another.
- Feel free to ask for help: around you are peers who are also navigating this path for the first time. Share your own experience and ideas with others.  Join Rocket.Chat to stay updated with the latest community announcements. 
- Your learning is meaningless if you just copy someone else’s solutions. When receiving help from others, always make sure you fully understand the “why”, “how”, and “purpose” behind the solution. Don’t be afraid to make mistakes. 
- Does the task seem impossible? Take a break, get some fresh air and clear your mind — this has helped many people. Maybe after that, the solution will come to you naturally.
- The learning process is just as important as the result. It’s not just about completing the task — it’s about understanding HOW to solve it. 

How to work with the project:

- Before starting, clone the project from GitLab into a repository with the same name.
- All files should be created inside the _src/_ folder of the cloned repository.
- After cloning the project, create a _develop_ branch and do all your development there. Then, push the _develop_ branch to GitLab.
- Your directory should not contain any files other than those specified in the assignments.

## Chapter I

Besides Docker Swarm, there are many other orchestration tools. One of the most popular is Kubernetes, a tool developed by Google. Kubernetes' main difference is its higher complexity and scale. Kubernetes is intended for more serious applications with a large number of services and complex interactions. It also has a number of additional built-in tools, such as an internal monitoring system.

## Chapter II

The result of this work must be a report containing detailed descriptions of how to implement each point, accompanied by screenshots. Prepare the report as a Markdown file in the `src` directory named `REPORT.MD`.

## Part 1. Ready-made manifest

### Task

1. Run a Kubernetes environment with 4 GB of memory.

2. Apply the manifest from the `/src/example` directory to the created Kubernetes environment.

3. Run the standard Kubernetes control panel with the command `minikube dashboard`.

4. Create tunnels to access the deployed services with the command `minikube service`.

5. Check that the deployed application is working by opening the application page in a browser (Apache service).

## Part 2. Your own manifest

### Task 

1. Create your own YAML files or manifests for the application from the first project (`/src/services`), implementing the following:
   - a configuration map with values for database hosts and services;
   - secrets containing the database password, login information, and cross-service authorization keys (found in the `application.properties` files);
   - pods and services for all application modules: PostgreSQL, RabbitMQ, and seven application services. Use a single replica for all services.

2. Run the application by sequentially applying manifests with the command: `kubectl apply -f <manifest>.yaml`.

3. Use the command `kubectl get <object_type> <object_name>` and `kubectl describe <object_type> <object_name>` to check the status of created objects (secrets, configuration maps, pods, and services) in the cluster. Add the results to the report.

4. Check the secret values by applying the command: `kubectl get secret my-secret -o jsonpath='{.data.password}' | base64 --decode` to decode the secret.

5. Check the logs of the application running in the cluster using the command: `kubectl logs <container_name>`. Add a screenshot to the report.

6. Create tunnels to access the gateway and session services.

7. Run the Postman functional tests to ensure that the application is working properly.

8. Run the standard Kubernetes control panel with the command `minikube dashboard`. Include screenshots from the dashboard in the report showing the current state of the cluster nodes, a list of running Pods, and other metrics such as CPU and memory utilization, Pod logs, and Pod configurations and secrets.

9. Update the application by adding a new dependency to the POM file and rebuild the application using the following deployment strategies. Measure the application redeployment time for each case and note the results in the report:
   - Recreate,
   - Rolling.