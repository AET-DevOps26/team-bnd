"""Unit tests for the OpenAPI error-response rewiring.

The end-to-end behaviour is covered via the app in test_main.py; this exercises
the schema walker directly on a hand-built spec so the branch that skips
non-operation path-item entries (parameters, summary, an op without responses)
is covered without needing a route that produces one.
"""

from app.errors import _ERROR_CONTENT, _rewire_error_responses


def test_rewire_skips_non_operation_entries_and_rewires_real_responses():
    schema = {
        "paths": {
            "/x": {
                "parameters": [{"name": "q"}],
                "summary": "shared summary",
                "get": {"operationId": "getX"},
                "post": {"responses": {"404": {"description": "nf"}}, "tags": ["ai"]},
            }
        }
    }

    _rewire_error_responses(schema)

    post_responses = schema["paths"]["/x"]["post"]["responses"]
    assert post_responses["404"]["content"] == _ERROR_CONTENT
    # the "ai" tag pulls in the provider-error contract
    assert {"500", "502", "504"} <= set(post_responses)
    # the responseless operation and the parameters/summary entries are left alone
    assert "responses" not in schema["paths"]["/x"]["get"]
