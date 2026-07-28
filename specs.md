Build a distributed batch processing application using Java21, Maven, Spring Boot, and Spring Batch. 

Support Windows, Linux, and macOS. Include start and stop scripts for sh and ps1. 

Support one-command startup for the full environment. Local mode. 

Run 6 identical Spring Boot instances. One instance becomes Master. The Master also processes a partition, so all 6 act as Workers. 

Use automatic Master election. Kubernetes mode. Simulate OpenShift deployment. 

Six identical pods. One becomes Master, all process partitions as Workers. Include Docker and Kubernetes manifests. 

All instances are identical. Master role is dynamic and rotates between runs. 

Use ShedLock if needed to guarantee a single active master. 

API endpoints to generate random banking data, trigger batch processing, get status and history, plus health and info. 

Generate lists of 10 to 100 bank accounts + any configurable number with transactions, store in H2. Reset on startup and allow regeneration. 

Batch processing reads from H2, splits work evenly across workers, generates one report per account, and tracks which worker handled each partition. 

Kubernetes mode uses Kafka remote partitioning with a consumer group. Local mode simulates distribution across six instances without Kafka. 

Use Round Robin or an alternative load balancing strategy. First receiver of the request becomes Master for that run. 

Frontend is a separate project using HTML, CSS, JavaScript, and NPM. 

Frontend features: Generate random data, start processing, show live status, show partition distribution and worker activity, show completed and failed jobs, click to visualize generate file, and history. 

Backend exposes REST controllers. Documentation must describe architecture, execution flow, master election, partitioning strategy, and deployment steps.

Use best practices of design, coding and tests. Use java records where suitable, and lombok where applicable, to reduce boiler plate code.

Author Information: Include an Author section in the README containing the author's full name, a short professional description, GitHub profile URL, and LinkedIn profile URL. 

Keep these values easy to update and clearly separated from the technical documentation.

Project name: distributed-batch-orchestrator
Author: Wallace Espindola
GitHub: github.com/wallaceespindola
LinkedIn: linkedin.com/in/wallaceespindola

Author Information: 
The project README must include an Author section identifying Wallace Espindola as the project author, with links to his GitHub (wallaceespindola) and LinkedIn (wallaceespindola) profiles.

Add a .gitignore and ignore AI common files, vscode and intellij ide files, windows and mac os files.

Move specs.md to /docs/specs/PRD_Specs.md at the end.

Add the main diagrams in /docs/diagrams, using puml and mermaid. Diagrams: Class, Components, Deployment, Sequence, Class model and ERD model.

Add unit tests with a reasonable test coverage.

Update readme.md and claude.md at the end of implementation.

Commit and push.
