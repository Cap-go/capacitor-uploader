export interface UploaderPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
