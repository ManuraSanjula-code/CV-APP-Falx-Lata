from utils.redis.RedisConnectionPool import RedisConnectionPool


class BulkRedisOperations:
    """Helper class for efficient bulk Redis operations."""

    def __init__(self, connection_pool: RedisConnectionPool):
        self.pool = connection_pool

    def bulk_hset_cvs(self, cv_data_list, chunk_size=50):
        """Efficiently store multiple CVs using pipelined operations."""
        results = []

        # Process in chunks to avoid memory issues
        for i in range(0, len(cv_data_list), chunk_size):
            chunk = cv_data_list[i:i + chunk_size]

            with self.pool.bulk_pipeline(transaction=False) as pipeline:
                chunk_results = []

                for cv_id, cv_data in chunk:
                    # Prepare Redis data
                    redis_data = {}
                    for key, value in cv_data.items():
                        if isinstance(value, (dict, list)):
                            import json
                            redis_data[key] = json.dumps(value)
                        else:
                            redis_data[key] = str(value)

                    # Add to pipeline
                    pipeline.hset(cv_id, mapping=redis_data)
                    chunk_results.append(cv_id)

                # Pipeline executes in context manager
                results.extend(chunk_results)

        return results

    def bulk_index_cvs(self, index_data_list, chunk_size=100):
        """Efficiently create search indexes for multiple CVs."""
        for i in range(0, len(index_data_list), chunk_size):
            chunk = index_data_list[i:i + chunk_size]

            with self.pool.bulk_pipeline(transaction=False) as pipeline:
                for cv_id, indexes in chunk:
                    for index_key, values in indexes.items():
                        if isinstance(values, list):
                            for value in values:
                                pipeline.sadd(f"index:{index_key}:{value.lower()}", cv_id)
                        else:
                            pipeline.sadd(f"index:{index_key}:{values.lower()}", cv_id)

    def batch_delete_cvs(self, cv_ids, chunk_size=50):
        """Efficiently delete multiple CVs and their indexes."""
        for i in range(0, len(cv_ids), chunk_size):
            chunk = cv_ids[i:i + chunk_size]

            with self.pool.bulk_pipeline(transaction=False) as pipeline:
                # Delete CV data
                for cv_id in chunk:
                    pipeline.delete(cv_id)

                # Remove from all indexes (this is expensive, consider optimization)
                all_index_keys = self.pool.client.keys("index:*")
                for index_key in all_index_keys:
                    for cv_id in chunk:
                        pipeline.srem(index_key, cv_id)

    def get_bulk_cv_data(self, cv_ids, chunk_size=50):
        """Efficiently retrieve multiple CV records."""
        results = {}

        for i in range(0, len(cv_ids), chunk_size):
            chunk = cv_ids[i:i + chunk_size]

            with self.pool.bulk_pipeline(transaction=False) as pipeline:
                # Queue all requests
                for cv_id in chunk:
                    pipeline.hgetall(cv_id)

                # Execute and collect results
                chunk_results = pipeline.execute()

                for cv_id, cv_data in zip(chunk, chunk_results):
                    if cv_data:
                        results[cv_id] = cv_data

        return results