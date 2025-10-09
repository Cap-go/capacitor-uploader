
import './style.css';
import { Uploader } from '@capgo/capacitor-uploader';

const plugin = Uploader;
const state = {};
state.lastUploadId = undefined;

const actions = [
{
              id: 'start-upload',
              label: 'Start upload',
              description: 'Starts uploading a file to the provided server.',
              inputs: [{ name: 'filePath', label: 'File path', type: 'text', placeholder: '/absolute/path/to/file' }, { name: 'serverUrl', label: 'Server URL', type: 'text', placeholder: 'https://example.com/upload' }, { name: 'headers', label: 'Headers (JSON)', type: 'textarea', value: '{
  "Content-Type": "application/octet-stream"
}', rows: 4 }, { name: 'method', label: 'HTTP method', type: 'select', value: 'POST', options: [{ value: 'POST', label: 'POST' }, { value: 'PUT', label: 'PUT' }] }, { name: 'mimeType', label: 'Mime type (optional)', type: 'text', placeholder: 'application/octet-stream' }, { name: 'uploadType', label: 'Upload type', type: 'select', value: 'binary', options: [{ value: 'binary', label: 'binary' }, { value: 'multipart', label: 'multipart' }] }, { name: 'fileField', label: 'Multipart file field (optional)', type: 'text', placeholder: 'file' }],
              run: async (values) => {
                if (!values.filePath) {
  throw new Error('Provide a file path.');
}
if (!values.serverUrl) {
  throw new Error('Provide a server URL.');
}
let headers = {};
if (values.headers) {
  try {
    headers = JSON.parse(values.headers);
  } catch (err) {
    throw new Error(\`Invalid headers JSON: ${err.message}\`);
  }
}
const options = {
  filePath: values.filePath,
  serverUrl: values.serverUrl,
  headers,
  method: values.method || 'POST',
  uploadType: values.uploadType || 'binary',
};
if (values.mimeType) options.mimeType = values.mimeType;
if (values.fileField) options.fileField = values.fileField;
const result = await plugin.startUpload(options);
state.lastUploadId = result.id;
return result;
              },
            },
{
              id: 'remove-upload',
              label: 'Remove upload',
              description: 'Cancels an upload by id.',
              inputs: [{ name: 'id', label: 'Upload id', type: 'text', placeholder: 'Reuse last id automatically' }],
              run: async (values) => {
                const id = values.id || state.lastUploadId;
if (!id) {
  throw new Error('Provide an upload id first.');
}
await plugin.removeUpload({ id });
return \`Upload ${id} removed.\`;
              },
            }
];

const actionSelect = document.getElementById('action-select');
const formContainer = document.getElementById('action-form');
const descriptionBox = document.getElementById('action-description');
const runButton = document.getElementById('run-action');
const output = document.getElementById('plugin-output');

function buildForm(action) {
  formContainer.innerHTML = '';
  if (!action.inputs || !action.inputs.length) {
    const note = document.createElement('p');
    note.className = 'no-input-note';
    note.textContent = 'This action does not require any inputs.';
    formContainer.appendChild(note);
    return;
  }
  action.inputs.forEach((input) => {
    const fieldWrapper = document.createElement('div');
    fieldWrapper.className = input.type === 'checkbox' ? 'form-field inline' : 'form-field';

    const label = document.createElement('label');
    label.textContent = input.label;
    label.htmlFor = `field-${input.name}`;

    let field;
    switch (input.type) {
      case 'textarea': {
        field = document.createElement('textarea');
        field.rows = input.rows || 4;
        break;
      }
      case 'select': {
        field = document.createElement('select');
        (input.options || []).forEach((option) => {
          const opt = document.createElement('option');
          opt.value = option.value;
          opt.textContent = option.label;
          if (input.value !== undefined && option.value === input.value) {
            opt.selected = true;
          }
          field.appendChild(opt);
        });
        break;
      }
      case 'checkbox': {
        field = document.createElement('input');
        field.type = 'checkbox';
        field.checked = Boolean(input.value);
        break;
      }
      case 'number': {
        field = document.createElement('input');
        field.type = 'number';
        if (input.value !== undefined && input.value !== null) {
          field.value = String(input.value);
        }
        break;
      }
      default: {
        field = document.createElement('input');
        field.type = 'text';
        if (input.value !== undefined && input.value !== null) {
          field.value = String(input.value);
        }
      }
    }

    field.id = `field-${input.name}`;
    field.name = input.name;
    field.dataset.type = input.type || 'text';

    if (input.placeholder && input.type !== 'checkbox') {
      field.placeholder = input.placeholder;
    }

    if (input.type === 'checkbox') {
      fieldWrapper.appendChild(field);
      fieldWrapper.appendChild(label);
    } else {
      fieldWrapper.appendChild(label);
      fieldWrapper.appendChild(field);
    }

    formContainer.appendChild(fieldWrapper);
  });
}

function getFormValues(action) {
  const values = {};
  (action.inputs || []).forEach((input) => {
    const field = document.getElementById(`field-${input.name}`);
    if (!field) return;
    switch (input.type) {
      case 'number': {
        values[input.name] = field.value === '' ? null : Number(field.value);
        break;
      }
      case 'checkbox': {
        values[input.name] = field.checked;
        break;
      }
      default: {
        values[input.name] = field.value;
      }
    }
  });
  return values;
}

function setAction(action) {
  descriptionBox.textContent = action.description || '';
  buildForm(action);
  output.textContent = 'Ready to run the selected action.';
}

function populateActions() {
  actionSelect.innerHTML = '';
  actions.forEach((action) => {
    const option = document.createElement('option');
    option.value = action.id;
    option.textContent = action.label;
    actionSelect.appendChild(option);
  });
  setAction(actions[0]);
}

actionSelect.addEventListener('change', () => {
  const action = actions.find((item) => item.id === actionSelect.value);
  if (action) {
    setAction(action);
  }
});

runButton.addEventListener('click', async () => {
  const action = actions.find((item) => item.id === actionSelect.value);
  if (!action) return;
  const values = getFormValues(action);
  try {
    const result = await action.run(values);
    if (result === undefined) {
      output.textContent = 'Action completed.';
    } else if (typeof result === 'string') {
      output.textContent = result;
    } else {
      output.textContent = JSON.stringify(result, null, 2);
    }
  } catch (error) {
    output.textContent = `Error: ${error?.message ?? error}`;
  }
});

populateActions();
