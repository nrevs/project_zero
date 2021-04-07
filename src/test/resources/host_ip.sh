#!/bin/bash


export HOST_IP=$(ip a | grep -A 1 "link-netnsid 0" | grep -Po 'inet \K[\d.]+' | grep -Po [0-9]+.[0-9]+.[0-9]+.)1

