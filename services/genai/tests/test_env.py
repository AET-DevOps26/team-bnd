"""Unit tests for the environment helpers."""

import os
from unittest.mock import patch

import pytest

from app.env import float_env, int_env


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


def test_float_env_returns_default_when_unset():
    with patch.dict(os.environ, {}, clear=False):
        os.environ.pop("SOME_FLOAT", None)
        assert float_env("SOME_FLOAT", 1.5, minimum=0.0) == 1.5


def test_float_env_returns_default_when_empty():
    with patch.dict(os.environ, {"SOME_FLOAT": ""}):
        assert float_env("SOME_FLOAT", 1.5, minimum=0.0) == 1.5


def test_float_env_parses_valid_value():
    with patch.dict(os.environ, {"SOME_FLOAT": "12.5"}):
        assert float_env("SOME_FLOAT", 1.5, minimum=0.0) == 12.5


def test_float_env_rejects_non_number():
    with patch.dict(os.environ, {"SOME_FLOAT": "abc"}):
        with pytest.raises(RuntimeError, match="must be a number"):
            float_env("SOME_FLOAT", 1.5, minimum=0.0)


def test_float_env_rejects_below_minimum():
    with patch.dict(os.environ, {"SOME_FLOAT": "-1.0"}):
        with pytest.raises(RuntimeError, match="must be >= 0"):
            float_env("SOME_FLOAT", 1.5, minimum=0.0)


def test_float_env_allows_zero_when_minimum_is_zero():
    with patch.dict(os.environ, {"SOME_FLOAT": "0"}):
        assert float_env("SOME_FLOAT", 1.5, minimum=0.0) == 0.0
