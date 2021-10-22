/* eslint-disable @typescript-eslint/no-var-requires */
const _ = require("lodash");
const delay = require("mocker-api/utils/delay");
const fs = require("fs");
const responseDelay = 500;

function readResponseDataFromFile(path) {
  return JSON.parse(fs.readFileSync(`./api-mocker/responses/${path}`, "utf8"));
}

const componentsData = readResponseDataFromFile(
  "components/componentsData.json"
);

const xesQueryResult = readResponseDataFromFile("xes/xes4.json");

const workspaces = [
  {
    id: "3a4c6121-4fa4-4017-b0eb-df67be468b95",
    name: "MyWorkspace1",
  },
  {
    id: "16d431d1-7ad1-475d-86a5-d4aa8578e92d",
    name: "MyOtherWorkspace",
  },
];

const dataStores = [
  {
    id: "6925b43f-cc6d-4320-8565-8388f2a7f6d7",
    organizationId: "1",
    name: "DataStore #1",
    size: 10_721_032,
    createdAt: "2020-09-25T06:32:28Z",
  },
  {
    id: "0096ec34-dd2f-47ee-9ccf-c804637df4dc",
    organizationId: "1",
    name: "DataStore #2",
    size: 102_490_001,
    createdAt: "2020-07-11T07:12:02Z",
  },
];

const dataConnectors = {
  "6925b43f-cc6d-4320-8565-8388f2a7f6d7": [
    {
      id: "7d28101b-5ef9-4b20-b57e-aaded99cfb2c",
      name: "PostgreSQL #1",
      lastConnectionStatus: false,
      lastConnectionStatusTimestamp: "9-12-2021",
      properties: {
        "connection-type": "PostgreSql",
        hostname: "80.10.22.100",
      },
    },
    {
      id: "aa8f65b4-7f93-44c7-a27c-7fc24cf6d1fb",
      name: "PostgreSQL #2",
      lastConnectionStatus: null,
      lastConnectionStatusTimestamp: null,
      properties: {
        "connection-type": "PostgreSql",
        hostname: "db.example.com",
      },
    },
    {
      id: "cdf13443-5449-4981-9755-a83768d9e855",
      name: "MongoDB connection",
      lastConnectionStatus: true,
      lastConnectionStatusTimestamp: "10-10-2020",
      properties: {},
    },
  ],
  "0096ec34-dd2f-47ee-9ccf-c804637df4dc": [],
};

const users = [
  {
    userId: 10,
    username: "ed@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "writer",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 12,
    username: "bob@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "writer",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 28,
    username: "tyron@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 29,
    username: "james@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 31,
    username: "andy@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "reader",
    password: "pass",
    locale: "en_GB",
  },
  {
    userId: 33,
    username: "eva@example.com",
    organizationId: "1",
    organizationName: "Org1",
    organizationRole: "owner",
    password: "pass",
    locale: "en_GB",
  },
];

const userSessions = [];

function createUUID() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0,
      v = c == "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const api = {
  "GET /api/organizations/:organizationId/data-stores": { data: dataStores },
  "GET /api/organizations/:organizationId/data-stores/:dataStoreId": (
    req,
    res
  ) => {
    const { dataStoreId } = req.params;

    const dataStore = _.find(dataStores, { id: dataStoreId });

    if (!dataStore) {
      return res.status(404).json();
    }

    return res.json({ data: dataStore });
  },
  "PATCH /api/organizations/:organizationId/data-stores/:dataStoreId": (
    req,
    res
  ) => {
    const { dataStoreId } = req.params;
    const dataStore = _.find(dataStores, { id: dataStoreId });

    if (!dataStore) {
      return res.status(404).json();
    }

    _.assign(dataStore, _.pick(req.body, _.keys(dataStore)));

    return res.status(204).json();
  },
  "DELETE /api/organizations/:organizationId/data-stores/:dataStoreId": (
    req,
    res
  ) => {
    const { dataStoreId } = req.params;
    const dataStore = _.find(dataStores, { id: dataStoreId });

    if (!dataStore) {
      return res.status(404).json();
    }

    _.remove(dataStores, { id: dataStoreId });

    return res.status(204).json();
  },
  "GET /api/organizations/:organizationId/data-stores/:dataStoreId/data-connectors": (
    req,
    res
  ) => {
    const { dataStoreId } = req.params;

    if (!_.has(dataConnectors, dataStoreId)) {
      return res.status(404).json();
    }

    return res.json({ data: dataConnectors[dataStoreId] });
  },
  "PATCH /api/organizations/:organizationId/data-stores/:dataStoreId/data-connectors/:dataConnectorId": (
    req,
    res
  ) => {
    const { dataStoreId, dataConnectorId } = req.params;
    const dataConnector = _.find(dataConnectors[dataStoreId], {
      id: dataConnectorId,
    });

    if (dataConnector == null) {
      return res.status(404).json();
    }

    _.assign(dataConnectors, _.pick(req.body, _.keys(dataConnector)));

    return res.status(204).json();
  },
  "DELETE /api/organizations/:organizationId/data-stores/:dataStoreId/data-connectors/:dataConnectorId": (
    req,
    res
  ) => {
    const { dataStoreId, dataConnectorId } = req.params;
    const dataConnector = _.find(dataConnectors[dataStoreId], {
      id: dataConnectorId,
    });

    if (dataConnector == null) {
      return res.status(404).json();
    }

    _.remove(dataConnectors[dataStoreId], { id: dataConnector });

    return res.status(204).json();
  },
  "POST /api/organizations/:organizationId/data-stores/:dataStoreId/data-connectors/test": (
    req,
    res
  ) => {
    const connectionConfiguration = req.body.data;

    if (_.has(connectionConfiguration, "connection-string")) {
      return res.json({
        data: {
          isValid: connectionConfiguration["connection-string"].length > 3,
        },
      });
    }

    const isValid =
      _.has(connectionConfiguration, "connector-name") &&
      _.has(connectionConfiguration, "username") &&
      _.has(connectionConfiguration, "password") &&
      _.has(connectionConfiguration, "server");

    return res.json({ data: { isValid } });
  },
  "POST /api/organizations/:organizationId/data-stores": (req, res) => {
    const dataStore = req.body.data;

    if (!dataStore) {
      return res.status(400).json();
    }

    dataStore.id = createUUID();
    workspaces.push(dataStore);

    return res.status(201).json({ data: dataStore });
  },
  "GET /api/organizations/:organizationId/workspaces": { data: workspaces },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: workspace });
  },
  "POST /api/organizations/:organizationId/workspaces": (req, res) => {
    const workspace = req.body.data;

    if (!workspace) {
      return res.status(400).json();
    }

    workspace.id = createUUID();
    workspaces.push(workspace);

    return res.status(201).json({ data: workspace });
  },
  "PATCH /api/organizations/:organizationId/workspaces/:workspaceId": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    _.assign(workspace, _.pick(req.body, _.keys(workspace)));

    return res.json({ data: workspace });
  },
  "DELETE /api/organizations/:organizationId/workspaces/:workspaceId": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspaceExists = _.some(workspaces, { id: workspaceId });

    if (!workspaceExists) {
      return res.status(404).json();
    }

    _.remove(workspaces, { id: workspaceId });

    return res.status(204).json();
  },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId/components": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: componentsData });
  },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId": (
    req,
    res
  ) => {
    const { workspaceId, componentId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });
    const component = _.find(componentsData, { id: componentId });

    if (!workspace || !component) {
      return res.status(404).json();
    }

    return res.json({ data: component });
  },
  "PUT /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId": (
    req,
    res
  ) => {
    const { workspaceId, componentId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });
    let component = _.find(componentsData, { id: componentId });

    if (!workspace) {
      return res.status(404).json();
    }

    if (!component) {
      component = { id: componentId };
      componentsData.push(component);
    }

    component.name = req.body.data.name;
    component.type = req.body.data.type;
    component.data = req.body.data.data;
    component.customizationData = req.body.data.customizationData;

    return res.status(204).json();
  },
  "DELETE /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId": (
    req,
    res
  ) => {
    const { workspaceId, componentId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });
    const component = _.find(componentsData, { id: componentId });

    if (!workspace || !component) {
      return res.status(404).json();
    }

    _.remove(componentsData, { id: componentId });

    return res.status(204).json();
  },
  "PATCH /api/organizations/:organizationId/workspaces/:workspaceId/layout": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    const layoutElements = req.body.data;

    componentsData.forEach((component) => {
      if (_.has(layoutElements, component.id)) {
        component.layout = layoutElements[component.id];
      }
    });

    return res.status(204).json();
  },
  "GET /api/organizations/:organizationId/workspaces/:workspaceId/components/:componentId/data": (
    req,
    res
  ) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: workspaceId });

    if (!workspace) {
      return res.status(404).json();
    }

    const datasetIndex = Math.floor(componentsData.length * Math.random());

    return res.json({ data: componentsData[datasetIndex].data });
  },
  "POST /api/users/session": (req, res) => {
    const credentials = req.body.data;

    if (
      !_.some(users, { username: credentials.login }) ||
      credentials.password !==
        _.find(users, { username: credentials.login }).password
    ) {
      return res.status(401).json();
    }

    const sessionToken =
      Math.random().toString(36).substring(2, 15) +
      Math.random().toString(36).substring(2, 15);

    _.set(userSessions, sessionToken, credentials.login);

    return res.status(201).json({
      data: {
        authorizationToken: `Bearer ${sessionToken}`,
      },
    });
  },
  "DELETE /api/users/session": (req, res) => {
    const { sessionId } = req.params;

    return _.unset(userSessions, sessionId)
      ? res.status(204).json()
      : res.status(404).json();
  },
  "POST /api/users": (req, res) => {
    const { userEmail, organizationName } = req.body.data;

    if (organizationName !== "org1" && userEmail !== "user1@example.com") {
      users.push({
        userId: 1,
        username: userEmail,
        organizationId: "1",
        organizationName,
        organizationRole: "owner",
        password: "pass",
        locale: "en_GB",
      });
      res.status(201).json();
    } else {
      res.status(400).json();
    }
  },
  "GET /api/users/me": (req, res) => {
    return res.json({
      data: {
        username: _.find(users, { userId: 1 }).username,
        locale: "en-GB",
      },
    });
  },
  "PATCH /api/users/me/password": (req, res) => {
    const { currentPassword, newPassword } = req.body.data;
    const user = _.find(users, { userId: 1 });

    if (user.password !== currentPassword) {
      return res
        .status(403)
        .json({ error: "The current password is not valid" });
    }

    user.password = newPassword;

    return res.status(202).json();
  },
  "PATCH /api/users/me/locale": (req, res) => {
    const { locale } = req.body.data;
    const user = _.find(users, { userId: 1 });

    user.locale = locale;

    return res.status(202).json();
  },
  "GET /api/users/me/organizations": (req, res) => {
    const user = _.find(users, { userId: 1 });

    return res.json({
      data: [
        {
          id: user.organizationId,
          name: user.organizationName,
          organizationRole: user.organizationRole,
        },
      ],
    });
  },
  "GET /api/organizations/:organizationId/members": (req, res) => {
    const { organizationId } = req.params;
    const members = _.filter(users, { organizationId });

    if (_.isEmpty(members)) {
      return res.status(403).json();
    }

    return res.status(200).json({ data: members });
  },
  "GET /api/data-stores/:dataStoreId/logs": (req, res) => {
    const { dataStoreId } = req.params;
    const { query } = req.query;

    return res.status(200).json({ data: xesQueryResult });
  },
};

module.exports = delay(api, responseDelay);
