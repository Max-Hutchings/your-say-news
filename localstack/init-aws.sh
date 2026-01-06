#!/bin/bash
# Initialize LocalStack resources

echo "Creating S3 bucket for video storage..."
awslocal s3 mb s3://post-videos

echo "LocalStack initialization complete!"

