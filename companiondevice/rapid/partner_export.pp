import '//releasetools/rapid/workflows/rapid.pp' as rapid

vars = rapid.create_vars() {}

copybara_workflow = vars.process_arguments.get('copybara_workflow', [null])[0]
allow_noop_migration = vars.process_arguments.get('allow_noop_migration',
                                                  [true])[0]
task_name = 'copybara.trigger_migration-' + copybara_workflow
candidate_name = vars.candidate_name

task_deps = [
  task_name: ['start'],
]

task_props = [
  task_name: [
    'allow_noop_migration': allow_noop_migration,
    'depot_path':
        '//depot/google3/third_party/java_src/android_app/companiondevice/copy.bara.sky',
    'workflow': copybara_workflow,
    'reference': candidate_name,
  ],
]

workflow release_with_copybara = rapid.workflow([task_deps, task_props]) {
  vars = @vars
}
