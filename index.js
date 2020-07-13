import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const { RNWebim: WebimNative } = NativeModules;
const emitter = new NativeEventEmitter(NativeModules.RNWebim);

export const webimEvents = {
  NEW_MESSAGE: 'newMessage',
  REMOVE_MESSAGE: 'removeMessage',
  EDIT_MESSAGE: 'changedMessage',
  CLEAR_DIALOG: 'allMessagesRemoved',
};

function parseNativeResponse(response) {
  return response || null;
}

function processError(e) {
  return new Error(e.message);
}

class Listener {
  constructor(remove) {
    this.remove = remove;
  }
}

class RNWebim {
  static resumeSession(accountName, location, userInfo, authToken) {
    return new Promise((resolve, reject) => {
      WebimNative.resumeSession(accountName, location, userInfo, authToken, e => reject(processError(e)), res => resolve(parseNativeResponse(res)));
    });
  }

  static destroySession() {
    return new Promise((resolve, reject) => {
      WebimNative.destroySession(e => reject(processError(e)), res => resolve(parseNativeResponse(res)));
    });
  }

  static getLastMessages(limit) {
    return new Promise((resolve, reject) => {
      WebimNative.getLastMessages(limit, e => reject(processError(e)), messages => resolve(parseNativeResponse(messages)));
    });
  }

  static getNextMessages(limit) {
    return new Promise((resolve, reject) => {
      WebimNative.getNextMessages(limit, e => reject(processError(e)), messages => resolve(parseNativeResponse(messages)));
    });
  }

  static getAllMessages() {
    return new Promise((resolve, reject) => {
      WebimNative.getAllMessages(e => reject(processError(e)), messages => resolve(parseNativeResponse(messages)));
    });
  }

  static send(message) {
    return new Promise((resolve, reject) => WebimNative.send(message, e => reject(processError(e)), id => resolve(id)));
  }

  static rateOperator(rate) {
    return new Promise((resolve, reject) => {
      WebimNative.rateOperator(rate, e => reject(processError(e)), () => resolve());
    });
  }

  static tryAttachFile() {
    return new Promise((resolve, reject) => {
      WebimNative.tryAttachFile(e => reject(processError(e)), async (...args) => {
        let uri;
        let name;
        let mime;
        let extension;
        if (Platform.OS === 'ios') {
          [{ uri, name, mime, extension }] = args;
        } else {
          [uri, name, mime, extension] = args;
        }
        try {
          await RNWebim.sendFile(uri, name, mime, extension);
          resolve();
        } catch (e) {
          reject(processError(e));
        }
      });
    });
  }

  static sendFile(uri, name, mime, extension) {
    return new Promise((resolve, reject) => WebimNative.sendFile(uri, name, mime, extension, reject, resolve));
  }

  static addListener(event, listener) {
    emitter.addListener(event, listener);
    return new Listener(() => RNWebim.removeListener(event, listener));
  }

  static removeListener(event, listener) {
    emitter.removeListener(event, listener);
  }

  static removeAllListeners(event) {
    emitter.removeAllListeners(event);
  }
}

export default RNWebim;
