# API

## Overview

The API is generated from the respective codebases, i.e., Spring and FastAPI. Two generator scripts are provided in the `api/` directory (`generate-spring-openapi.sh` and `generate-genai-openapi.sh`) to regenerate the respective `.yaml` files, which are then combined into `openapi.yaml` using `merge-openapi.sh`.

If pre-commit hooks are installed, the API spec is automatically regenerated.

The respective client implementations can then be generated via tools as described in the "Best Practices Microservices" section on Artemis.

Changes to the API should lead to an updated version number, such that clients don't break on update. For this purpose we use semantic versioning (cf. [semver.org](https://semver.org/))
