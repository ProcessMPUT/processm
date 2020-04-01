/* eslint-disable @typescript-eslint/no-var-requires */
const _ = require("lodash");

const workspaces = [
  {
    id: 1,
    name: "MyWorkspace1"
  },
  {
    id: 3,
    name: "MyOtherWorkspace"
  }
];

const userSessions = {};

const api = {
  "GET /api/workspaces": { data: workspaces },
  "GET /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: Number(workspaceId) });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: workspace });
  },
  "POST /api/workspaces": (req, res) => {
    const workspace = req.body;

    if (!workspace) {
      return res.status(400).json();
    }

    workspace.id = _.last(workspaces).id + 1;
    workspaces.push(workspace);

    return res.json({ data: workspace });
  },
  "PATCH /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: Number(workspaceId) });

    if (!workspace) {
      return res.status(404).json();
    }

    _.assign(workspace, _.pick(req.body, _.keys(workspace)));

    return res.json({ data: workspace });
  },
  "DELETE /api/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspaceExists = _.some(workspaces, { id: Number(workspaceId) });

    if (!workspaceExists) {
      return res.status(404).json();
    }

    _.remove(workspaces, { id: Number(workspaceId) });

    return res.status(204).json();
  },
  "POST /api/users/session": (req, res) => {
    const credentials = req.body.data;

    if (credentials.password != "pass") {
      return res.status(401).json();
    }

    const sessionToken =
      Math.random()
        .toString(36)
        .substring(2, 15) +
      Math.random()
        .toString(36)
        .substring(2, 15);

    _.set(userSessions, sessionToken, credentials.username);

    return res.json({
      data: {
        authorizationToken: `Bearer ${sessionToken}`
      }
    });
  },
  "DELETE /api/users/session/:sessionId": (req, res) => {
    const { sessionId } = req.params;

    return _.unset(userSessions, sessionId)
      ? res.status(204).json()
      : res.status(404).json();
  }
};

module.exports = api;
