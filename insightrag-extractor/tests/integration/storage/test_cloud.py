import pytest

from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs
from app.config.config import MinIOSettings
from app.storage.cloud import MinioStorage


@pytest.fixture(scope="session")
def minio_container():
    """Create a Minio container for testing."""
    container = (
        DockerContainer("pgsty/minio:latest")
        .with_env("MINIO_ROOT_USER", "minioadmin")
        .with_env("MINIO_ROOT_PASSWORD", "minioadmin")
        .with_command("server /data")
        .with_exposed_ports(9000)
    )
    with container:
        wait_for_logs(container, r"API:", timeout=30)
        yield container


@pytest.fixture
def minio_client(minio_container):
    """Create a Minio client for testing."""
    random_host_port = minio_container.get_exposed_port(9000)
    host_ip = minio_container.get_container_host_ip()
    config = MinIOSettings(
        MINIO_ENDPOINT=f"{host_ip}:{random_host_port}",
        MINIO_ACCESS_KEY="minioadmin",
        MINIO_SECRET_KEY="minioadmin",
        MINIO_SECURE=False,
    )

    storage = MinioStorage(config)

    if not storage.storage.bucket_exists("test-bucket"):
        storage.storage.make_bucket("test-bucket")
    return storage


@pytest.mark.asyncio
async def test_minio_storage_save_and_load(minio_client):
    bucket = "test-bucket"
    key = "test.json"
    data = {"message": "Hello, Minio!"}
    await minio_client.save_file(bucket, key, data)

    assert await minio_client.exists(bucket, key) is True
    loaded_data = await minio_client.load_file(bucket, key)
    assert loaded_data == data


@pytest.mark.asyncio
async def test_minio_storage_delete_file_with_prefix(minio_client):
    bucket = "test-bucket"
    key1 = "folder/prefix/test-1.json"
    data1 = {"message": "content-1"}
    await minio_client.save_file(bucket, key1, data1)
    assert await minio_client.exists(bucket, key1) is True

    key2 = "folder/prefix/test-2.json"
    data2 = {"message": "content-2"}
    await minio_client.save_file(bucket, key2, data2)
    assert await minio_client.exists(bucket, key2) is True

    await minio_client.delete_target(bucket, "folder/prefix/")

    assert await minio_client.exists(bucket, key1) is False
    assert await minio_client.exists(bucket, key2) is False


@pytest.mark.asyncio
async def test_minio_storage_delete_file_specific(minio_client):
    bucket = "test-bucket"
    key = "folder/prefix/test-1.json"
    data = {"message": "content-1"}
    await minio_client.save_file(bucket, key, data)
    assert await minio_client.exists(bucket, key) is True

    await minio_client.delete_target(bucket, key)

    assert await minio_client.exists(bucket, key) is False
