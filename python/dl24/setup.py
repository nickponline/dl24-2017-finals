#!/usr/bin/env python3

from setuptools import setup, find_packages

setup(
    name='dl24',
    description='Utilities for Deadline24 contest',
    packages=find_packages(),
    install_requires=['prometheus_client', 'watchdog'],
    extras_require={
        'vis': ['cairocffi', 'matplotlib', 'gbulb']
    },
    python_requires='>=3.5',
    author='Bruce Merry, Carl Hultquist, Nicholas Pilkington',
    version='2017.0',
    entry_points={
        'console_scripts': [
            'dl24proxy = dl24.proxy:main'
        ]
    }
)
