FROM docker.elastic.co/logstash/logstash:7.16.1
RUN rm -f /usr/share/logstash/pipeline/logstash.conf
COPY logstash_conf.in /usr/share/logstash/pipeline/logstash.conf
run curl -L 'https://jdbc.postgresql.org/download/postgresql-42.2.18.jar' -o /usr/share/logstash/postgresql.jar
