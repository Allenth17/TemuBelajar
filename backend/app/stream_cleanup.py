from backend.app.stream_manager import cleanup_streams

if __name__ == "__main__":
    cleanup_streams()
    print("✅ Stream cleanup complete.")
