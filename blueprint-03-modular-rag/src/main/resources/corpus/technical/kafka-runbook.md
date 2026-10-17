# Consumer lag alert
When consumer lag exceeds 10000 messages, first check whether the consumer group is rebalancing. Scale consumers up to the partition count, never beyond it.

# Broker disk full
If a Kafka broker disk is over 85 percent, lower retention.ms on the largest topics and add disk before it reaches 95 percent.
