import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from filters import should_forward


def test_mode_all_forwards_everything():
    assert should_forward(text="hello world", mode="all", keywords=[]) is True
    assert should_forward(text="crypto news", mode="all", keywords=["crypto"]) is True


def test_mode_include_matches_keyword():
    assert should_forward(text="Bitcoin price up", mode="include", keywords=["bitcoin"]) is True


def test_mode_include_no_match():
    assert should_forward(text="weather report", mode="include", keywords=["bitcoin"]) is False


def test_mode_include_empty_keywords_forwards_nothing():
    assert should_forward(text="any text", mode="include", keywords=[]) is False


def test_mode_exclude_no_match_forwards():
    assert should_forward(text="weather report", mode="exclude", keywords=["bitcoin"]) is True


def test_mode_exclude_match_blocks():
    assert should_forward(text="Bitcoin price up", mode="exclude", keywords=["bitcoin"]) is False


def test_mode_exclude_empty_keywords_forwards_everything():
    assert should_forward(text="any text", mode="exclude", keywords=[]) is True


def test_case_insensitive():
    assert should_forward(text="BITCOIN is rising", mode="include", keywords=["bitcoin"]) is True
    assert should_forward(text="Bitcoin news", mode="exclude", keywords=["BITCOIN"]) is False


def test_partial_word_match():
    assert should_forward(text="cryptocurrency market", mode="include", keywords=["crypto"]) is True


def test_none_text_treated_as_empty():
    assert should_forward(text=None, mode="include", keywords=["crypto"]) is False
    assert should_forward(text=None, mode="all", keywords=[]) is True
