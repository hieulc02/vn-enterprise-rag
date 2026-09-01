import pytest
from faker import Faker


@pytest.fixture
def fake():
    fake_instance = Faker(locale="vi_VN")
    Faker.seed(0)
    return fake_instance
