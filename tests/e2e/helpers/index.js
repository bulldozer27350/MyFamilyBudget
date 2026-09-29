'use strict';

// Point d entree unique : const { API, FRONT, expectBackendCall, ... } = require('./helpers');

module.exports = Object.assign(
  {},
  require('./constants'),
  require('./backend'),
  require('./browser'),
  require('./state')
);
