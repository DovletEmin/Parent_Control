from app.tasks.celery_app import celery_app


@celery_app.task(name="cleanup.remove_old_locations")
def remove_old_locations(days: int = 90):
    """Remove location records older than N days."""
    pass


@celery_app.task(name="cleanup.remove_old_app_usage")
def remove_old_app_usage(days: int = 180):
    """Remove app usage records older than N days."""
    pass
