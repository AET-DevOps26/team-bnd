# API
The API is generated from the respective codebases, i.e., Spring and FastAPI. Two scripts are provided in the `api/` directory to regenerate the respective `.yaml` files, these can be then merged using the `merge-openapi.sh` scripts.

If pre-commit hooks are installed, the API spec is automatically regenerated.

The respective client implementations can then be generated via tools as described in the "Best Practices Microservices" section on Artemis.

Changes to the API should lead to an updated version number, such that clients don't break on update.
