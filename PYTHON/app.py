import os
import datetime
from flask import Flask, jsonify
from config import Config
from utils.redis_client import RedisConnection
from routes.auth_routes import auth_bp
from routes.cv_routes import cv_bp
from routes.api_routes import api_bp


def create_app():
    """Create and configure Flask application."""
    app = Flask(__name__)
    config = Config()

    # Configure Flask
    app.config['SECRET_KEY'] = config.SECRET_KEY
    app.config['MAX_CONTENT_LENGTH'] = config.MAX_CONTENT_LENGTH

    # Ensure directories exist
    os.makedirs(config.UPLOAD_FOLDER, exist_ok=True)
    os.makedirs(config.USER_PDF_STORAGE_BASE, exist_ok=True)

    # Test Redis connection on startup
    redis_conn = RedisConnection()
    if not redis_conn.ping():
        print("Warning: Could not connect to Redis. Please ensure Redis is running.")
    else:
        print("Successfully connected to Redis.")

    # Register blueprints
    app.register_blueprint(auth_bp)
    app.register_blueprint(cv_bp)
    app.register_blueprint(api_bp, url_prefix='/api')

    # Root endpoint
    @app.route('/')
    def index():
        return jsonify({
            "message": "CV Parser API is running",
            "version": "2.0.0",
            "endpoints": {
                "authentication": ["/register", "/login", "/profile"],
                "cv_management": ["/upload", "/api/view/<cv_id>", "/api/cv/<cv_id>", "/api/download_pdf/<cv_id>"],
                "search_and_stats": ["/api/search", "/api/stats", "/api/indexes"],
                "audit": ["/api/audit_logs", "/api/user_activity/<username>"]
            },
            "timestamp": datetime.datetime.utcnow().isoformat() + 'Z'
        }), 200

    # Health check endpoint
    @app.route('/health')
    def health_check():
        redis_conn = RedisConnection()
        redis_status = "healthy" if redis_conn.ping() else "unhealthy"

        return jsonify({
            "status": "healthy" if redis_status == "healthy" else "degraded",
            "services": {
                "redis": redis_status,
                "api": "healthy"
            },
            "timestamp": datetime.datetime.utcnow().isoformat() + 'Z'
        }), 200 if redis_status == "healthy" else 503

    # Error handlers
    @app.errorhandler(404)
    def not_found(error):
        return jsonify({'error': 'Endpoint not found'}), 404

    @app.errorhandler(500)
    def internal_error(error):
        return jsonify({'error': 'Internal server error'}), 500

    @app.errorhandler(413)
    def file_too_large(error):
        return jsonify({'error': 'File too large. Maximum size is 100MB.'}), 413

    return app


def main():
    """Main application entry point."""
    app = create_app()

    # Get configuration
    config = Config()
    debug_mode = os.environ.get('FLASK_DEBUG', 'True').lower() == 'true'
    host = os.environ.get('FLASK_HOST', '0.0.0.0')
    port = int(os.environ.get('FLASK_PORT', 5000))

    print(f"Starting CV Parser API on {host}:{port}")
    print(f"Debug mode: {debug_mode}")

    app.run(debug=debug_mode, host=host, port=port)


if __name__ == '__main__':
    main()