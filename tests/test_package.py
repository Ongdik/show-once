import engine


def test_패키지를_불러올_수_있다():
    assert engine.__version__ == "0.1.0"
