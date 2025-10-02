import os
import datetime
import atexit
import signal
import sys
from flask import Flask, jsonify
from config import Config
from utils.redis_client import RedisConnection
from utils.redis.RedisConnectionPool import RedisConnectionPool
from routes.auth_routes import auth_bp
from routes.cv_routes import cv_bp
from routes.api_routes import api_bp

from utils.BulkProcessingQueue import get_processing_queue, BulkProcessingQueue


def create_app():
    """Create and configure Flask application with bulk processing support."""
    app = Flask(__name__)
    config = Config()

    # Configure Flask for bulk operations
    app.config['SECRET_KEY'] = config.SECRET_KEY
    app.config['MAX_CONTENT_LENGTH'] = config.MAX_CONTENT_LENGTH

    # Ensure directories exist
    os.makedirs(config.UPLOAD_FOLDER, exist_ok=True)
    os.makedirs(config.USER_PDF_STORAGE_BASE, exist_ok=True)

    # Initialize Redis connection pool
    redis_pool = RedisConnectionPool()
    if not redis_pool.ping():
        print("Warning: Could not connect to Redis. Please ensure Redis is running.")
        return None
    else:
        print("Successfully connected to Redis with connection pooling.")

    # Initialize bulk processing queue
    processing_queue = get_processing_queue()
    print(f"Initialized bulk processing queue with {processing_queue.max_workers} workers")

    # Register blueprints
    app.register_blueprint(auth_bp)
    app.register_blueprint(cv_bp)
    app.register_blueprint(api_bp, url_prefix='/api')

    # Enhanced root endpoint with bulk processing info
    @app.route('/')
    def index():
        queue_status = processing_queue.get_queue_status()
        redis_stats = redis_pool.get_connection_stats()

        return jsonify({
            "message": "CV Parser API with Bulk Processing Support",
            "version": "2.1.0",
            "bulk_processing": {
                "max_files_per_upload": config.MAX_FILES_PER_UPLOAD,
                "max_concurrent_workers": config.MAX_WORKERS,
                "queue_status": queue_status,
                "supported_file_types": list(config.ALLOWED_EXTENSIONS)
            },
            "redis_connection_pool": redis_stats,
            "endpoints": {
                "authentication": ["/register", "/login", "/profile"],
                "cv_management": [
                    "/upload",
                    "/upload/status/<batch_id>",
                    "/upload/cancel/<batch_id>",
                    "/api/view/<cv_id>",
                    "/api/cv/<cv_id>",
                    "/api/download_pdf/<cv_id>"
                ],
                "search_and_stats": ["/api/search", "/api/stats", "/api/indexes"],
                "audit": ["/api/audit_logs", "/api/user_activity/<username>"],
                "bulk_monitoring": ["/api/queue_status", "/api/system_resources"]
            },
            "timestamp": datetime.datetime.utcnow().isoformat() + 'Z'
        }), 200

    # Enhanced health check endpoint
    @app.route('/health')
    def health_check():
        redis_status = "healthy" if redis_pool.ping() else "unhealthy"
        queue_status = processing_queue.get_queue_status()

        # Check if system is overloaded
        resources_ok, resource_msg = processing_queue.resource_monitor.is_resources_available()

        system_status = "healthy"
        if redis_status != "healthy":
            system_status = "degraded"
        elif not resources_ok:
            system_status = "overloaded"
        elif queue_status['queue_size'] > config.MAX_FILES_PER_UPLOAD * 0.8:
            system_status = "busy"

        return jsonify({
            "status": system_status,
            "services": {
                "redis": redis_status,
                "processing_queue": "healthy" if queue_status['active_jobs'] >= 0 else "failed",
                "api": "healthy"
            },
            "system_resources": {
                "available": resources_ok,
                "message": resource_msg
            },
            "queue_metrics": queue_status,
            "timestamp": datetime.datetime.utcnow().isoformat() + 'Z'
        }), 200 if system_status == "healthy" else 503

    # New endpoint for queue monitoring
    @app.route('/api/queue_status')
    def queue_status():
        """Get detailed queue status."""
        try:
            status = processing_queue.get_queue_status()
            return jsonify(status), 200
        except Exception as e:
            return jsonify({'error': f'Error getting queue status: {str(e)}'}), 500

    # New endpoint for system resource monitoring
    @app.route('/api/system_resources')
    def system_resources():
        """Get system resource usage."""
        try:
            resources_ok, resource_msg = processing_queue.resource_monitor.is_resources_available()
            resource_stats = processing_queue.resource_monitor.get_stats()

            return jsonify({
                'resources_available': resources_ok,
                'message': resource_msg,
                'statistics': resource_stats
            }), 200
        except Exception as e:
            return jsonify({'error': f'Error getting resource status: {str(e)}'}), 500

    # New endpoint for batch management
    @app.route('/api/admin/batches')
    def list_active_batches():
        """List all active processing batches."""
        try:
            # Get all batch results from Redis
            redis_conn = RedisConnection()
            batch_keys = redis_conn.client.keys("batch_result:*")

            batches = []
            for key in batch_keys:
                try:
                    batch_data = redis_conn.client.get(key)
                    if batch_data:
                        import json
                        batch_info = json.loads(batch_data.decode('utf-8'))
                        batches.append(batch_info)
                except Exception as e:
                    print(f"Error loading batch {key}: {e}")
                    continue

            # Add current processing batches
            queue_status = processing_queue.get_queue_status()

            return jsonify({
                'completed_batches': batches,
                'current_queue_status': queue_status
            }), 200

        except Exception as e:
            return jsonify({'error': f'Error listing batches: {str(e)}'}), 500

    # Cleanup endpoint
    @app.route('/api/admin/cleanup')
    def cleanup_old_data():
        """Clean up old processing data."""
        try:
            # Clean up old queue jobs
            cleaned_jobs = processing_queue.cleanup_old_jobs(hours_old=24)

            # Clean up old batch results from Redis
            redis_conn = RedisConnection()
            batch_keys = redis_conn.client.keys("batch_result:*")
            cleaned_batches = 0

            cutoff_time = datetime.datetime.now() - datetime.timedelta(hours=48)

            for key in batch_keys:
                try:
                    batch_data = redis_conn.client.get(key)
                    if batch_data:
                        import json
                        batch_info = json.loads(batch_data.decode('utf-8'))
                        completed_at = datetime.datetime.fromisoformat(batch_info.get('completed_at', ''))

                        if completed_at < cutoff_time:
                            redis_conn.client.delete(key)
                            cleaned_batches += 1

                except Exception as e:
                    print(f"Error cleaning batch {key}: {e}")
                    continue

            return jsonify({
                'cleaned_jobs': cleaned_jobs,
                'cleaned_batches': cleaned_batches,
                'message': 'Cleanup completed successfully'
            }), 200

        except Exception as e:
            return jsonify({'error': f'Cleanup error: {str(e)}'}), 500

    # Error handlers
    @app.errorhandler(404)
    def not_found(error):
        return jsonify({'error': 'Endpoint not found'}), 404

    @app.errorhandler(500)
    def internal_error(error):
        return jsonify({'error': 'Internal server error'}), 500

    @app.errorhandler(413)
    def file_too_large(error):
        return jsonify({
            'error': f'File too large. Maximum size is {config.MAX_CONTENT_LENGTH // (1024 * 1024)}MB.'
        }), 413

    @app.errorhandler(429)
    def too_many_requests(error):
        return jsonify({
            'error': 'Too many requests. Processing queue is full. Please try again later.'
        }), 429

    return app


def setup_graceful_shutdown(processing_queue: BulkProcessingQueue):
    """Setup graceful shutdown for the processing queue."""

    def signal_handler(sig, frame):
        print(f"\nReceived signal {sig}, shutting down gracefully...")
        processing_queue.shutdown(wait_for_completion=False)
        sys.exit(0)

    def cleanup_on_exit():
        print("Application shutting down, cleaning up...")
        processing_queue.shutdown(wait_for_completion=False)

    # Register signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    # Register cleanup function
    atexit.register(cleanup_on_exit)


def main():
    """Main application entry point with bulk processing support."""

    # Create app
    app = create_app()
    if app is None:
        print("Failed to create application. Check Redis connection.")
        return 1

    # Setup graceful shutdown
    processing_queue = get_processing_queue()
    setup_graceful_shutdown(processing_queue)

    # Get configuration
    config = Config()
    debug_mode = os.environ.get('FLASK_DEBUG', 'False').lower() == 'true'
    host = os.environ.get('FLASK_HOST', '0.0.0.0')
    port = int(os.environ.get('FLASK_PORT', 8000))

    print("=" * 60)
    print("CV Parser API with Bulk Processing")
    print("=" * 60)
    print(f"Server: {host}:{port}")
    print(f"Debug mode: {debug_mode}")
    print(f"Max files per upload: {config.MAX_FILES_PER_UPLOAD}")
    print(f"Max concurrent workers: {config.MAX_WORKERS}")
    print(f"Max memory usage: {config.MAX_MEMORY_USAGE_MB}MB")
    print(f"Processing queue size: {processing_queue.max_queue_size}")
    print("=" * 60)
    print("Bulk Processing Features:")
    print("- Asynchronous processing for large batches")
    print("- Resource monitoring and management")
    print("- Queue-based job processing with retry logic")
    print("- Real-time progress tracking")
    print("- Automatic cleanup of old data")
    print("=" * 60)

    try:
        app.run(
            debug=debug_mode,
            host=host,
            port=port,
            threaded=True,
            use_reloader=False  # Disable reloader to prevent duplicate queue initialization
        )
    except KeyboardInterrupt:
        print("\nShutting down gracefully...")
    except Exception as e:
        print(f"Error starting server: {e}")
        return 1

    return 0


if __name__ == '__main__':
    sys.exit(main())