package it.bollettalab.platform;

@FunctionalInterface
public interface ActivitySink {
  void record(ActivityEvent event);
}
