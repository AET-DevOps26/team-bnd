"""Unit tests for the environment helpers."""

import os
from unittest.mock import patch

import pytest

from app.env import int_env


def test_int_env_returns_default_when_unset():
    with patch.dict(os.environ, {}, clear=False):
        os.environ.pop("SOME_INT", None)
        assert int_env("SOME_INT", 7, minimum=1) == 7


def test_int_env_returns_default_when_empty():
    with patch.dict(os.environ, {"SOME_INT": ""}):
        assert int_env("SOME_INT", 7, minimum=1) == 7


def test_int_env_parses_valid_value():
    with patch.dict(os.environ, {"SOME_INT": "12"}):
        assert int_env("SOME_INT", 7, minimum=1) == 12


def test_int_env_rejects_non_integer():
    with patch.dict(os.environ, {"SOME_INT": "abc"}):
        with pytest.raises(RuntimeError, match="must be an integer"):
            int_env("SOME_INT", 7, minimum=1)


def test_int_env_rejects_below_minimum():
    with patch.dict(os.environ, {"SOME_INT": "0"}):
        with pytest.raises(RuntimeError, match="must be >= 1"):
            int_env("SOME_INT", 7, minimum=1)


def test_int_env_allows_zero_when_minimum_is_zero():
    with patch.dict(os.environ, {"SOME_INT": "0"}):
        assert int_env("SOME_INT", 7, minimum=0) == 0
