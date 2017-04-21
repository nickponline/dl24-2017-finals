import asyncio
import logging
import functools
import inspect
import re
import os
import prometheus_client


COMMAND_TIME = prometheus_client.Summary('dl24_command_time_seconds', 'Time between issuing command and receiving reply', labelnames=['command'])
RESPONSES_OK = prometheus_client.Counter('dl24_responses_ok_total', 'Total number of successful requests', labelnames=['command'])
RESPONSES_FAILED = prometheus_client.Counter('dl24_responses_failed_total', 'Total number of commands with failed responses', labelnames=['command'])
RESPONSES_PROTOCOL_ERROR = prometheus_client.Counter('dl24_responses_protocol_error_total', 'Total number of commands with protocol errors in response', labelnames=['command'])


class Failure(RuntimeError):
    """A FAILED response from the server"""
    def __init__(self, errno, strerror):
        super(Failure, self).__init__()
        self.errno = errno
        self.strerror = strerror

    def __str__(self):
        return '[{}] {}'.format(self.errno, self.strerror)


class ProtocolError(RuntimeError):
    """An error in parsing the protocol from the server"""
    def __init__(self, message, data):
        super(ProtocolError, self).__init__()
        self.message = message
        self.data = data

    def __str__(self):
        return 'Failed to parse "{}": {}'.format(self.data, self.message)


def add_arguments(parser):
    """Add arguments for endpoint, username and password to an argument parser."""
    parser.add_argument('endpoint', metavar='HOST:PORT', help='Remote server')
    parser.add_argument('--user', help='Login name [$DL24_USER]')
    parser.add_argument('--pass', dest='password', help='Password [$DL24_PASS]')


def command(name):
    """Decorator for a command that takes care of sending the command to the
    server and waiting for the OK response. The wrapped function is
    responsible for parsing the rest of the reply (if any).

    If the function takes keyword-only arguments, they are *not* passed to
    the server.
    """
    def decorator(func):
        signature = inspect.signature(func)
        @functools.wraps(func)
        async def wrapper(self, *args, **kwargs):
            bind = signature.bind(self, *args, **kwargs)
            bind.apply_defaults()
            fields = [name]
            for arg in bind.args[1:]:
                fields.append(str(arg))
            with COMMAND_TIME.labels(name).time():
                self.writeline(' '.join(fields))
                try:
                    await self.read_ok()
                    ret = await func(*bind.args, **bind.kwargs)
                except Failure:
                    RESPONSES_FAILED.labels(name).inc()
                    raise
                except ProtocolError:
                    RESPONSES_PROTOCOL_ERROR.labels(name).inc()
                    raise
                RESPONSES_OK.labels(name).inc()
                return ret
        # Force the labels to exist immediately
        RESPONSES_FAILED.labels(name)
        RESPONSES_PROTOCOL_ERROR.labels(name)
        RESPONSES_OK.labels(name)
        return wrapper
    return decorator


class ClientBase(object):
    """A base class for clients wrapping a network protocol.

    Instances should not be created directly; instead use the asynchronous
    class method :meth:`create`.
    """

    def __init__(self, host, port, user, password):
        self.reader = None
        self.writer = None
        self.host = host
        self.port = port
        self.user = user
        self.password = password

    async def readline(self, expected=None, parse_failed=False):
        """Retrieves a single line, with the trailing newline removed.

        Parameters
        ----------
        expected : str or regex, optional
            If given, raise a :exc:`ProtocolError` if the read value is different
        parse_failed : bool, optional
            If true, raise a :exc:`Failure` if the line starts with FAILED

        Raises
        ------
        Failure
            if the line is a FAILED message and `parse_failed` is true
        ProtocolError
            if the line does not match `expected`
        EOFError
            if the server disconnected before sending anything
        """
        line = await self.reader.readline()
        line = line.decode('utf-8', errors='replace')
        if line == '':
            raise EOFError()
        line = line.rstrip()
        if parse_failed and line.startswith('FAILED '):
            fields = line.split(' ', 2)
            try:
                errno = int(fields[1])
                message = fields[2]
            except Exception as error:
                raise ProtocolError(str(error), line)
            raise Failure(errno, message)
        if hasattr(expected, 'match'):
            if not expected.fullmatch(line):
                raise ProtocolError('expected line matching {}'.format(expected.pattern), line)
        elif expected is not None:
            if line != expected:
                raise ProtocolError('expected {}'.format(expected), line)
        return line

    async def read_ok(self):
        """Read a line and expect that it is OK, otherwise raising a :exc:`Failure`"""
        return await self.readline(expected='OK', parse_failed=True)

    def writeline(self, line):
        """Writes a line to the socket, adding a newline."""
        data = (line + '\n').encode('utf-8')
        self.writer.write(data)

    async def connect(self):
        """Asynchronously create a client and connect it to the server."""
        self.reader, self.writer = await asyncio.open_connection(
            self.host, self.port, limit=2**20)
        # Do the login protocol
        prompt = await self.readline(expected=re.compile('LOGIN|PROXY-NOLOGIN'))
        if prompt == 'LOGIN':
            self.writeline(self.user)
            await self.readline(expected='PASS')
            self.writeline(self.password)
            await self.read_ok()

    @classmethod
    def connect_args(cls, args):
        """Extract command-line arguments to pass to :meth:`create`.

        The `args` are returned from a :cls:`~argparse.ArgumentParser`
        which has been augmented by :func:`add_arguments`.
        """
        if ':' not in args.endpoint:
            raise ValueError('Endpoint must have the form HOST:PORT')
        host, port = args.endpoint.rsplit(':', 1)
        if args.user is not None:
            user = args.user
        else:
            user = os.environ.get('DL24_USER')
        if user is None:
            raise ValueError('User not set')
        if args.password is not None:
            password = args.password
        else:
            password = os.environ.get('DL24_PASS')
        if password is None:
            raise ValueError('Password not set')
        return host, port, user, password

    def close(self):
        if self.writer is not None:
            self.writer.close()

    @command('WAIT')
    async def wait(self):
        await self.readline(expected=re.compile('WAITING .*'))
        await self.read_ok()
