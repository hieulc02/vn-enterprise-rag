import pytest

import orjson as json

from unittest.mock import MagicMock, patch

from app.storage.cloud import MinioStorage
from app.config.config import MinIOSettings

from minio.error import S3Error


@pytest.fixture
def minio_config():
    return MinIOSettings(
        MINIO_ENDPOINT="localhost:9000",
        MINIO_ACCESS_KEY="dummy_access_key",
        MINIO_SECRET_KEY="dummy_secret_key",
        MINIO_SECURE=False,
    )


@pytest.fixture
def minio_storage(minio_config):
    with patch("app.storage.cloud.Minio") as mock_minio:
        yield MinioStorage(config=minio_config)


@pytest.mark.asyncio
async def test_exists_returns_false_on_no_such_key(minio_storage):

    mock_error = S3Error(
        code="NoSuchKey",
        message="The specified key does not exist.",
        resource="/bucket/key",
        request_id="123",
        host_id="123",
        response=MagicMock(),
    )

    minio_storage.storage.stat_object.side_effect = mock_error

    result = await minio_storage.exists("bucket", "missing-key")

    assert result is False
    minio_storage.storage.stat_object.assert_called_once_with("bucket", "missing-key")


@pytest.mark.asyncio
async def test_load_file_cleans_up_resources_on_error(minio_storage):

    mock_response = MagicMock()
    mock_response.read.return_value = b"{invalid json"
    minio_storage.storage.get_object.return_value = mock_response

    with pytest.raises(json.JSONDecodeError):
        await minio_storage.load_file("bucket", "invalid-json-key")

    mock_response.close.assert_called_once()
    mock_response.release_conn.assert_called_once()
