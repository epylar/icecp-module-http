#!/bin/bash

set -e

INSTALL_DIR=/home/icecp/modules/"<%= name %>"/"<%= version %>"-"<%= iteration %>"
INSTALL_DIR_LINK=/home/icecp/icecp-module-http

after_install()
{
  echo "Performing post-install steps for <%= name %> version=<%= version %> iteration=<%= iteration %>"

  # reset the symbolic link to the version of software just installed
  ln -f -s $INSTALL_DIR $INSTALL_DIR_LINK
}
after_install
