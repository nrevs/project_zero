#!/bin/bash



while [ : ]
do
    echo 'TESTING - CURL HTTP POST: '$HOST_IP':443 *************************'
    echo ''
    echo ''
    sleep 5
    curl -k -X POST -d 'name=linuxize&email=linuxize@example.com' $HOST_IP:443
    echo ''
    echo ''
    echo 'TEST RESULTS - CURL HTTP POST: '$HOST_IP':443 **********************'
    echo ''
    echo ''
    sleep 5

    echo 'TESTING CURL HTTP GET ON '$HOST_IP':443 *****************************'
    echo ''
    echo ''
    curl -k $HOST_IP:443
    echo ''
    echo ''
    echo 'TEST URL HTTP GET ON '$HOST_IP':443 *********************************'
    echo ''
    echo ''
    echo ''
    echo ''
    echo 'NEXT TEST - CURL HTTP POST: '$HOST_IP':443 *************************'
    echo ''
    echo ''
    sleep 5
    curl -X POST -d 'name=linuxize&email=linuxize@example.com' $HOST_IP:443
    echo ''
    echo ''
    echo 'TEST RESULTS - CURL HTTP POST: '$HOST_IP':443 **********************'
    echo ''
    echo ''
    echo ''
    echo ''

    echo 'NEXT TEST - OPENSSL CERTIFICATES: '$HOST_IP':443 *******************'
    echo ''
    echo ''
    sleep 5
    openssl s_client -showcerts -servername $HOST_IP:443 -connect $HOST_IP:443 <nul | openssl x509 -text | grep -i "DNS After" 2>nul
    echo ''
    echo ''
    echo 'TEST RESULTS - OPENSSL CERTIFICATES: '$HOST_IP':443 ****************'
    echo ''
    echo ''
    echo 'repeating tests...'
    echo ''
    echo ''
    echo ''
    echo ''
    sleep 10
done

