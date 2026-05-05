# team-bnd

## Project summary
Alexandria is a document management and knowledge extraction platform. Users upload documents, e.g., research papers, reports, manuals, meeting notes, and the system automatically organizes, tags, and summarizes them.
Users get a concise summary and can ask questions about their documents, instead of having to read through a 40-page report to find what they need.

The core workflow is: Upload a document, get an auto-generated summary with extracted key entities, browse and search your knowledge base, and optionally query the GenAI for specific answers concerning your uploaded content.

## Overview
TODO

## Setup

### Git Repository
This repository uses pre-commit hooks. Install [pre-commit](https://pre-commit.com/) and run `pre-commit install`. This will automatically run these scripts on each `git commit`:
- TODO

### Server
To start up the spring-boot service, a Dockerfile is provided. To use it:
1. Navigate to `services/spring/`
2. Build the image: `docker build -t spring`
3. Run the container: `docker run -p 8080:8080 spring`
4. Perform API calls: e.g. `curl http://localhost:8080/hello`
5. Enjoy!

### Client
TODO

### GenAI
TODO
