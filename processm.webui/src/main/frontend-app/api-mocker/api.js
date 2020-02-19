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

const api = {
  "GET /api/:userId/workspaces": { data: workspaces },
  "GET /api/:userId/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: Number(workspaceId) });

    if (!workspace) {
      return res.status(404).json();
    }

    return res.json({ data: workspace });
  },
  "POST /api/:userId/workspaces": (req, res) => {
    const workspace = req.body;

    if (!workspace) {
      return res.status(400).json();
    }

    workspace.id = _.last(workspaces).id + 1;
    workspaces.push(workspace);

    return res.json({ data: workspace });
  },
  "PATCH /api/:userId/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspace = _.find(workspaces, { id: Number(workspaceId) });

    if (!workspace) {
      return res.status(404).json();
    }

    _.assign(workspace, _.pick(req.body, _.keys(workspace)));

    return res.json({ data: workspace });
  },
  "DELETE /api/:userId/workspaces/:workspaceId": (req, res) => {
    const { workspaceId } = req.params;
    const workspaceExists = _.some(workspaces, { id: Number(workspaceId) });

    if (!workspaceExists) {
      return res.status(404).json();
    }

    _.remove(workspaces, { id: Number(workspaceId) });

    return res.status(204).json();
  }
};

module.exports = api;
