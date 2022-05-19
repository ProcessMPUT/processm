alter table etl_configurations
    add column sample_size int null;
/**
  both refresh & sample_size null -> a non-periodic, normal process
  refresh not null & sample_size null -> a periodic, normal process
  refresh null & sample_size not null -> a sampling process
  refresh not null & sample_size not null -> a forbidden configuration
 */
ALTER TABLE etl_configurations
    ADD CONSTRAINT chk_sampling_process_is_not_periodic CHECK (num_nonnulls(refresh, sample_size) <= 1);

/*
 If sample_size is not null then must be enabled
 sample_size != null -> enabled
 sample_size == null OR enabled
 */
ALTER TABLE etl_configurations
    ADD CONSTRAINT chk_sampling_process_is_enabled CHECK (sample_size IS NULL or enabled);