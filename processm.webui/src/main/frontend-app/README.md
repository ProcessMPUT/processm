# frontend-app

## Tools

The project developed using the following tools:
* Vue.js framework (v2.6.11),
* Vue CLI (v4.2.2) - serves as a webpack wrapper,
* Vuetify - Vue.js components library,
* TypeScript,
* NPM.

The following commands should be executed in the context of `processm/processm.webui/src/main/frontend-app/` directory.

## Project setup
```
npm install
npm run generate-api-sources
```

### Compiles and hot-reloads for development
```
npm run serve
```

### Compiles and minifies for production
```
npm run build
```

### Lints and fixes files
```
npm run lint
```

### Prettier
```
npx prettier --write '**/*.ts'
```

## Release

Preparing the front-end package for release doesn't require any extra components. You can execute the following without the need of installing development tools.

### Prepares for hosting with processm.sevices

Execute `mvn package` for `processm/processm.webui` module. Once it completed you can launch `processm.services`.

### Prepares for deployment

Make sure `mvn package` is executed during package preparation.

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).

## Environment

Visual Studio Code with Vetur extension recommended as IDE.
Another recommended tool is Vue.js devtools extension for web browsers.