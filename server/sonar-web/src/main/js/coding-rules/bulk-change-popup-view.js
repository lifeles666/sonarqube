define([
    'common/popup',
    'coding-rules/bulk-change-modal-view',
    'templates/coding-rules'
], function (PopupView, BulkChangeModalView) {

  var $ = jQuery;

  return PopupView.extend({
    template: Templates['coding-rules-bulk-change-popup'],

    events: {
      'click .js-bulk-change': 'doAction'
    },

    doAction: function (e) {
      var action = $(e.currentTarget).data('action'),
          param = $(e.currentTarget).data('param');
      new BulkChangeModalView({
        app: this.options.app,
        action: action,
        param: param
      }).render();
    },

    serializeData: function () {
      var query = this.options.app.state.get('query'),
          profileKey = query.qprofile,
          profile = _.findWhere(this.options.app.qualityProfiles, { key: profileKey }),
          activation = '' + query.activation;

      return {
        qualityProfile: profileKey,
        qualityProfileName: profile != null ? profile.name : null,
        allowActivateOnProfile: profileKey != null && activation === 'false',
        allowDeactivateOnProfile: profileKey != null && activation === 'true'
      };
    }
  });

});
